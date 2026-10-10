package com.dbgram.controller;

import com.dbgram.domain.media.UploadRecord;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.MediaUploadService;
import com.dbgram.util.AppException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController @RequestMapping("/api/uploads") @RequiredArgsConstructor
public class TusUploadController {
    private final MediaUploadService uploads;
    @RequestMapping(value={"","/{id}"},method=RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.noContent().headers(base()).header("Tus-Extension","creation,termination,checksum,expiration")
            .header("Tus-Max-Size",Long.toString(uploads.maxSize())).header("Tus-Checksum-Algorithm","sha1,sha256").build();
    }
    @PostMapping
    public ResponseEntity<Void> create(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value="Tus-Resumable",required=false) String version,
            @RequestHeader(value="Upload-Length",required=false) Long length,
            @RequestHeader(value="Upload-Metadata",required=false) String metadata) throws IOException {
        version(version);
        if(length==null)throw AppException.badRequest("Upload-Length is required");
        UploadRecord upload=uploads.create(user.id(),length,metadata);
        return ResponseEntity.status(201).headers(headers(upload)).header("Location","/api/uploads/"+upload.getId()).build();
    }
    @PatchMapping("/{id}")
    public ResponseEntity<Void> patch(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,
            @RequestHeader(value="Tus-Resumable",required=false) String version,
            @RequestHeader(value="Upload-Offset",required=false) Long offset,
            @RequestHeader(value="Upload-Checksum",required=false) String checksum,HttpServletRequest request) throws IOException {
        version(version);
        if(!"application/offset+octet-stream".equals(request.getContentType()))
            return ResponseEntity.status(415).headers(base()).build();
        if(offset==null || offset<0)throw AppException.badRequest("Upload-Offset is required and must be nonnegative");
        if(request.getContentLengthLong()>MediaUploadService.MAX_CHUNK) return ResponseEntity.status(413).headers(base()).build();
        return ResponseEntity.noContent().headers(headers(uploads.append(id,user.id(),offset,request.getInputStream(),checksum))).build();
    }
    @RequestMapping(value="/{id}",method=RequestMethod.HEAD)
    public ResponseEntity<Void> head(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,
            @RequestHeader(value="Tus-Resumable",required=false) String version) {
        version(version); return ResponseEntity.ok().headers(headers(uploads.head(id,user.id()))).build();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID id,
            @RequestHeader(value="Tus-Resumable",required=false) String version) throws IOException {
        version(version); uploads.terminate(id,user.id()); return ResponseEntity.noContent().headers(base()).build();
    }
    @ExceptionHandler(MediaUploadService.ChecksumMismatchException.class)
    public ResponseEntity<Void> checksumMismatch() {return ResponseEntity.status(460).headers(base()).build();}
    @ExceptionHandler(AppException.class)
    public ResponseEntity<?> invalid(AppException ex){return ResponseEntity.status(ex.getStatus()).headers(base()).body(java.util.Map.of("success",false,"message",ex.getMessage()));}
    private void version(String value){if(!"1.0.0".equals(value))throw new AppException(HttpStatus.PRECONDITION_FAILED,"TUS_VERSION","Tus-Resumable must be 1.0.0");}
    private HttpHeaders base(){HttpHeaders h=new HttpHeaders();h.set("Tus-Resumable","1.0.0");h.set("Tus-Version","1.0.0");h.setCacheControl("no-store");return h;}
    private HttpHeaders headers(UploadRecord u){
        HttpHeaders h=base();h.set("Upload-Offset",Long.toString(u.getUploadOffset()));h.set("Upload-Length",Long.toString(u.getUploadLength()));
        if(u.getMetadata()!=null)h.set("Upload-Metadata",u.getMetadata());
        if("UPLOADING".equals(u.getStatus()))h.set("Upload-Expires",DateTimeFormatter.RFC_1123_DATE_TIME.format(u.getExpiresAt().atOffset(ZoneOffset.UTC)));
        return h;
    }
}
