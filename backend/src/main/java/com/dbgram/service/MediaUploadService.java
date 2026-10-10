package com.dbgram.service;

import com.dbgram.crypto.MediaProvenanceService;
import com.dbgram.domain.media.UploadRecord;
import com.dbgram.repository.UploadRecordRepository;
import com.dbgram.util.AppException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class MediaUploadService {
    public static final long MAX_CHUNK = 8L * 1024 * 1024;
    private final UploadRecordRepository records;
    private final MediaProvenanceService provenance;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final Path root;
    private final byte[] signingKey;
    private final long maxSize;
    private final boolean transcode;

    public MediaUploadService(UploadRecordRepository records, MediaProvenanceService provenance,
            ObjectMapper json, JdbcTemplate jdbc,
            @Value("${media.storage-path:./data/media}") String root,
            @Value("${jwt.secret}") String secret,
            @Value("${media.max-upload-bytes:104857600}") long maxSize,
            @Value("${media.transcode-enabled:false}") boolean transcode) throws IOException {
        this.records=records; this.provenance=provenance; this.json=json; this.jdbc=jdbc;
        this.root=Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        this.signingKey=secret.getBytes(StandardCharsets.UTF_8); this.maxSize=maxSize;
        this.transcode=transcode;
    }
    public long maxSize() { return maxSize; }
    public Path file(UUID id) { return root.resolve(id + ".bin"); }
    public Path variantDirectory(UUID id) { return root.resolve(id + "-variants"); }

    @Transactional(rollbackFor=Exception.class)
    public UploadRecord create(UUID owner, long length, String rawMetadata) throws IOException {
        if(length<0) throw AppException.badRequest("Upload-Length must be nonnegative");
        if(length>maxSize) throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE,"UPLOAD_TOO_LARGE","Maximum upload size is " + maxSize + " bytes");
        // Serialize reservation checks per account so parallel creates cannot exceed quotas.
        jdbc.queryForObject("select id from users where id=? for update", UUID.class, owner);
        if(records.countByOwnerIdAndStatus(owner,"UPLOADING")>=20 || records.reservedBytes(owner)+length>10L*maxSize)
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,"UPLOAD_QUOTA","Upload storage quota reached");
        Map<String,String> metadata=parseMetadata(rawMetadata);
        String filename=metadata.getOrDefault("filename","upload").replaceAll("[\\\\/\\p{Cntrl}]","_");
        if(filename.length()>200) filename=filename.substring(filename.length()-200);
        String mime=metadata.getOrDefault("filetype",metadata.getOrDefault("type","application/octet-stream")).toLowerCase(Locale.ROOT);
        if(!mime.matches("[a-z0-9.+-]+/[a-z0-9.+-]+") || mime.length()>100) throw AppException.badRequest("Invalid file type");
        UploadRecord upload=new UploadRecord(); upload.setId(UUID.randomUUID()); upload.setOwnerId(owner);
        upload.setUploadLength(length); upload.setFilename(filename); upload.setMimeType(mime); upload.setMetadata(rawMetadata);
        Files.createFile(file(upload.getId()));
        if(length==0) complete(upload);
        return records.save(upload);
    }

    @Transactional(readOnly=true)
    public UploadRecord head(UUID id, UUID owner) { return owned(id,owner,false); }

    @Transactional(rollbackFor=Exception.class)
    public UploadRecord append(UUID id,UUID owner,long offset,InputStream input,String checksum) throws IOException {
        UploadRecord upload=owned(id,owner,true);
        if(upload.getUploadOffset()!=offset) throw AppException.conflict("Upload-Offset does not match; resume with HEAD");
        if("READY".equals(upload.getStatus())) throw AppException.conflict("Upload already complete");
        byte[] chunk=input.readNBytes((int)MAX_CHUNK+1);
        if(chunk.length>MAX_CHUNK || offset+chunk.length>upload.getUploadLength())
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE,"CHUNK_TOO_LARGE","Chunk exceeds remaining length or 8 MiB limit");
        verifyChecksum(chunk,checksum);
        Path file=file(id);
        try(RandomAccessFile output=new RandomAccessFile(file.toFile(),"rw")) {
            if(output.length()<offset) throw AppException.internal("Upload storage is unavailable");
            // If a previous process stopped between fsync and transaction commit, discard uncommitted bytes.
            output.setLength(offset); output.seek(offset); output.write(chunk); output.getFD().sync();
        }
        upload.setUploadOffset(offset+chunk.length);
        if(upload.getUploadOffset()==upload.getUploadLength()) complete(upload);
        return records.save(upload);
    }

    @Transactional(rollbackFor=Exception.class)
    public void terminate(UUID id,UUID owner) throws IOException {
        UploadRecord upload=owned(id,owner,true);
        upload.setStatus("TERMINATED"); records.save(upload);
        Files.deleteIfExists(file(id));
        Path variants=variantDirectory(id);
        if(Files.isDirectory(variants)) {
            try(var paths=Files.list(variants)) { for(Path path:paths.toList()) Files.deleteIfExists(path); }
            Files.deleteIfExists(variants);
        }
        jdbc.update("update media_assets set upload_status='FAILED' where id=?",id);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> metadata(UUID id,UUID viewer) {
        UploadRecord upload=readable(id,viewer);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",id); result.put("filename",upload.getFilename()); result.put("mimeType",upload.getMimeType());
        result.put("size",upload.getUploadLength()); result.put("offset",upload.getUploadOffset()); result.put("status",upload.getStatus());
        result.put("sha256",upload.getSha256()); result.put("merkleRoot",upload.getMerkleRoot());
        result.put("transcodeStatus",upload.getTranscodeStatus());
        result.put("url","READY".equals(upload.getStatus()) ? signedUrl(id,viewer) : null);
        result.put("variants","READY".equals(upload.getTranscodeStatus())?Map.of("hls",variantUrl(id,viewer,"master.m3u8"),"dash",variantUrl(id,viewer,"manifest.mpd")):Map.of());
        return result;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine(UUID viewer) {
        return records.findTop100ByOwnerIdOrderByCreatedAtDesc(viewer).stream()
            .filter(r->!"TERMINATED".equals(r.getStatus())).map(r->metadata(r.getId(),viewer)).toList();
    }

    @Transactional(readOnly=true)
    public Map<String,Object> proof(UUID id, UUID viewer, int index) throws IOException {
        UploadRecord upload=readable(id,viewer);
        if(!"READY".equals(upload.getStatus())) throw AppException.conflict("Upload is not complete");
        List<String> leaves=json.readValue(upload.getLeafHashes(),new TypeReference<>(){});
        if(index<0 || index>=leaves.size()) throw AppException.badRequest("Leaf index is outside the file");
        return Map.of("sha256",upload.getSha256(),"merkleRoot",upload.getMerkleRoot(),"leafIndex",index,
            "leafHash",leaves.get(index),"leafCount",leaves.size(),"leafBytes",MediaProvenanceService.LEAF_BYTES,
            "siblings",provenance.proof(leaves,index),"anchored",false,"method","SHA-256; duplicate final odd leaf; binary concatenation");
    }

    @Transactional(readOnly=true)
    public UploadRecord content(UUID id,UUID principal,UUID signedViewer,Long expiry,String signature) {
        UUID viewer=principal;
        if(viewer==null) {
            if(signedViewer==null || expiry==null || signature==null || expiry<Instant.now().getEpochSecond()
                    || expiry>Instant.now().plusSeconds(901).getEpochSecond()) throw AppException.unauthorized("Media link expired");
            String expected=sign(id,signedViewer,expiry);
            if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))
                throw AppException.unauthorized("Invalid media link");
            viewer=signedViewer;
        }
        UploadRecord upload=readable(id,viewer);
        if(!"READY".equals(upload.getStatus()) || !Files.isRegularFile(file(id))) throw AppException.notFound("Media",id);
        return upload;
    }

    private UploadRecord owned(UUID id,UUID owner,boolean lock) {
        UploadRecord upload=(lock?records.lockById(id):records.findById(id)).orElseThrow(()->AppException.notFound("Upload",id));
        if(!upload.getOwnerId().equals(owner)) throw AppException.notFound("Upload",id);
        if("TERMINATED".equals(upload.getStatus()) || ("UPLOADING".equals(upload.getStatus()) && upload.getExpiresAt().isBefore(Instant.now())))
            throw new AppException(HttpStatus.GONE,"UPLOAD_EXPIRED","Upload expired or was terminated");
        return upload;
    }

    private UploadRecord readable(UUID id,UUID viewer) {
        UploadRecord upload=records.findById(id).orElseThrow(()->AppException.notFound("Media",id));
        if("TERMINATED".equals(upload.getStatus()) || "QUARANTINED".equals(upload.getStatus())) throw AppException.notFound("Media",id);
        Long active=jdbc.queryForObject("select count(*) from users where id=? and status='ACTIVE'",Long.class,viewer);
        if(active==null || active==0) throw AppException.unauthorized("Account unavailable");
        if(upload.getOwnerId().equals(viewer)) return upload;
        Long profile=jdbc.queryForObject("""
            select count(*) from users u where u.status='ACTIVE' and (u.avatar_url=? or u.cover_url=?)
            and not exists(select 1 from blocked_users b where (b.blocker_id=u.id and b.blocked_id=?) or (b.blocked_id=u.id and b.blocker_id=?))
            """,Long.class,"/api/media/"+id,"/api/media/"+id,viewer,viewer);
        if(profile!=null&&profile>0)return upload;
        Long allowed=jdbc.queryForObject("""
            select count(*) from posts p where check_post_access(p.id,?)
            and exists (select 1 from jsonb_array_elements(p.blocks) b
                where b->'data'->>'mediaId'=? or b->'data'->>'url'=?)
            """,Long.class,viewer,id.toString(),"/api/media/"+id);
        if(allowed==null || allowed==0) throw AppException.notFound("Media",id);
        return upload;
    }

    private void complete(UploadRecord upload) throws IOException {
        Path path=file(upload.getId());
        // Never trust client MIME for inline delivery; scriptable formats stay download-only.
        upload.setMimeType(detectMime(path));
        var manifest=provenance.inspect(path);
        upload.setSha256(manifest.sha256()); upload.setMerkleRoot(manifest.merkleRoot());
        upload.setLeafHashes(json.writeValueAsString(manifest.leaves())); upload.setStatus("READY");
        upload.setTranscodeStatus(upload.getMimeType().startsWith("video/") ? (transcode?"QUEUED":"DISABLED") : "NOT_REQUIRED");
        String kind=upload.getMimeType().startsWith("image/")?"IMAGE":upload.getMimeType().startsWith("video/")?"VIDEO":upload.getMimeType().startsWith("audio/")?"AUDIO":"DOCUMENT";
        jdbc.update("""
            insert into media_assets(id,uploader_id,original_url,media_type,mime_type,file_size_bytes,sha256_hash,merkle_root,provenance_status,tus_upload_id,upload_status,created_at)
            values (?,?,?,?,?,?,?,?,'BATCHED',?,'READY',now()) on conflict(id) do nothing
            """,upload.getId(),upload.getOwnerId(),"/api/media/"+upload.getId(),kind,upload.getMimeType(),upload.getUploadLength(),
            manifest.sha256(),manifest.merkleRoot(),upload.getId().toString());
    }

    private String detectMime(Path path) throws IOException {
        byte[] b; try(InputStream input=Files.newInputStream(path)){b=input.readNBytes(32);}
        if(b.length>=8 && Arrays.equals(Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
        if(b.length>=3 && b[0]==(byte)255 && b[1]==(byte)216 && b[2]==(byte)255)return "image/jpeg";
        String prefix=new String(b,StandardCharsets.ISO_8859_1);
        if(prefix.startsWith("GIF87a")||prefix.startsWith("GIF89a"))return "image/gif";
        if(prefix.startsWith("RIFF")&&prefix.length()>=12&&prefix.substring(8,12).equals("WEBP"))return "image/webp";
        if(prefix.length()>=12&&prefix.substring(4,8).equals("ftyp"))return "video/mp4";
        if(b.length>=4&&b[0]==0x1a&&b[1]==0x45&&b[2]==(byte)0xdf&&b[3]==(byte)0xa3)return "video/webm";
        if(prefix.startsWith("OggS"))return "audio/ogg";
        if(prefix.startsWith("ID3")||(b.length>1&&b[0]==(byte)255&&(b[1]&0xe0)==0xe0))return "audio/mpeg";
        if(prefix.startsWith("%PDF-"))return "application/pdf";
        return "application/octet-stream";
    }

    private Map<String,String> parseMetadata(String raw) {
        if(raw==null || raw.isBlank())return Map.of();
        if(raw.length()>4096)throw AppException.badRequest("Upload-Metadata is too long");
        Map<String,String> values=new HashMap<>();
        for(String entry:raw.split(",")) {
            String[] pair=entry.trim().split(" ",2);
            if(!pair[0].matches("[A-Za-z0-9_-]+")||values.containsKey(pair[0]))throw AppException.badRequest("Invalid Upload-Metadata key");
            try { values.put(pair[0],pair.length==1?"":new String(Base64.getDecoder().decode(pair[1]),StandardCharsets.UTF_8)); }
            catch(IllegalArgumentException e){throw AppException.badRequest("Upload-Metadata values must be base64");}
        }
        return values;
    }

    private void verifyChecksum(byte[] chunk,String checksum) {
        if(checksum==null)return;
        String[] parts=checksum.split(" ",2);
        if(parts.length!=2 || !Set.of("sha1","sha256").contains(parts[0]))throw AppException.badRequest("Checksum must use sha1 or sha256");
        try {
            byte[] expected=Base64.getDecoder().decode(parts[1]);
            byte[] actual=MessageDigest.getInstance(parts[0].equals("sha1")?"SHA-1":"SHA-256").digest(chunk);
            if(!MessageDigest.isEqual(expected,actual))throw new ChecksumMismatchException();
        }catch(IllegalArgumentException e){throw AppException.badRequest("Invalid checksum encoding");}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private String signedUrl(UUID id,UUID viewer) {
        long expires=Instant.now().plusSeconds(900).getEpochSecond();
        return "/api/media/"+id+"?viewer="+viewer+"&expires="+expires+"&signature="+sign(id,viewer,expires);
    }
    public String variantUrl(UUID id,UUID viewer,String name){return signedUrl(id,viewer).replace("/api/media/"+id,"/api/media/"+id+"/variants/"+name);}

    @Transactional(readOnly=true)
    public Map<String,Object> verifyStored(UUID id,UUID viewer) throws IOException{
        UploadRecord upload=readable(id,viewer);
        if(!"READY".equals(upload.getStatus()))throw AppException.conflict("Upload is not ready");
        var current=provenance.inspect(file(id));
        return Map.of("valid",current.sha256().equals(upload.getSha256())&&current.merkleRoot().equals(upload.getMerkleRoot()),
            "sha256",current.sha256(),"merkleRoot",current.merkleRoot(),"anchored",false);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> batch(UUID viewer,List<UUID> ids){
        if(ids==null||ids.isEmpty()||ids.size()>100)throw AppException.badRequest("Select 1 to 100 uploaded files");
        List<UUID> sorted=ids.stream().distinct().sorted().toList();List<String> hashes=new ArrayList<>();
        for(UUID id:sorted){UploadRecord record=owned(id,viewer,false);if(!"READY".equals(record.getStatus()))throw AppException.conflict("All media must be complete");hashes.add(record.getSha256());}
        List<Map<String,Object>> leaves=new ArrayList<>();for(int i=0;i<sorted.size();i++)leaves.add(Map.of("id",sorted.get(i),"sha256",hashes.get(i),"index",i,"siblings",provenance.proof(hashes,i)));
        return Map.of("merkleRoot",provenance.merkleRoot(hashes),"leaves",leaves,"anchored",false,"algorithm","SHA-256","purpose","Root prepared for external anchoring; no blockchain transaction has been submitted");
    }
    private String sign(UUID id,UUID viewer,long expiry) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id+":"+viewer+":"+expiry).getBytes(StandardCharsets.UTF_8)));
        }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public static class ChecksumMismatchException extends RuntimeException {}

    @Transactional(rollbackFor=Exception.class)
    public void cleanupExpired() throws IOException {
        for(UploadRecord candidate:records.findTop100ByStatusAndExpiresAtBefore("UPLOADING",Instant.now())) {
            UploadRecord record=records.lockById(candidate.getId()).orElse(null);
            if(record!=null&&"UPLOADING".equals(record.getStatus())&&record.getExpiresAt().isBefore(Instant.now())){
                Files.deleteIfExists(file(record.getId()));record.setStatus("TERMINATED");records.save(record);
            }
        }
    }

    @Transactional
    public void retryTranscode(UUID id,UUID owner){
        UploadRecord record=owned(id,owner,true);
        if(!transcode)throw AppException.badRequest("Video transcoding is disabled on this server");
        if(!"READY".equals(record.getStatus())||!record.getMimeType().startsWith("video/"))throw AppException.badRequest("Only completed videos can be transcoded");
        if(!Set.of("FAILED","DISABLED").contains(record.getTranscodeStatus()))throw AppException.conflict("Video is already queued, processing or ready");
        record.setTranscodeStatus("QUEUED");records.save(record);
    }
}
