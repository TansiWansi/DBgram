package com.dbgram.service;

import com.dbgram.crypto.MediaProvenanceService;
import com.dbgram.domain.media.UploadRecord;
import com.dbgram.repository.UploadRecordRepository;
import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MediaUploadServiceTest {
    @TempDir Path directory;
    private final UUID owner=UUID.randomUUID();
    private final Map<UUID,UploadRecord> rows=new HashMap<>();
    private final UploadRecordRepository records=mock(UploadRecordRepository.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private MediaUploadService service;
    @BeforeEach void setup() throws Exception {
        when(records.save(any(UploadRecord.class))).thenAnswer(call->{UploadRecord r=call.getArgument(0);rows.put(r.getId(),r);return r;});
        when(records.findById(any(UUID.class))).thenAnswer(call->Optional.ofNullable(rows.get(call.getArgument(0))));
        when(records.lockById(any(UUID.class))).thenAnswer(call->Optional.ofNullable(rows.get(call.getArgument(0))));
        service=freshService();
    }
    private MediaUploadService freshService()throws Exception{return new MediaUploadService(records,new MediaProvenanceService(),new ObjectMapper(),jdbc,directory.toString(),"test-signing-key-is-long-enough-for-unit-tests",104857600,false);}
    @Test void resumesAfterRestartAndRejectsStaleOffsetsAndBadChecksums() throws Exception {
        byte[] first={1,2,3},last={4,5,6};
        var upload=service.create(owner,6,"filename dGVzdC5iaW4=");UUID id=upload.getId();
        service.append(id,owner,0,new ByteArrayInputStream(first),null);
        assertEquals(3,service.head(id,owner).getUploadOffset());
        AppException stale=assertThrows(AppException.class,()->service.append(id,owner,0,new ByteArrayInputStream(last),null));
        assertEquals(HttpStatus.CONFLICT,stale.getStatus());
        assertThrows(MediaUploadService.ChecksumMismatchException.class,()->service.append(id,owner,3,new ByteArrayInputStream(last),"sha256 "+Base64.getEncoder().encodeToString(new byte[32])));
        assertEquals(3,service.head(id,owner).getUploadOffset());
        // Simulate a process crash after fsync but before the database committed the chunk.
        Files.write(service.file(id),new byte[]{99,99},StandardOpenOption.APPEND);
        service=freshService();
        var ready=service.append(id,owner,3,new ByteArrayInputStream(last),null);
        assertEquals("READY",ready.getStatus());assertEquals(6,ready.getUploadOffset());
        assertArrayEquals(new byte[]{1,2,3,4,5,6},Files.readAllBytes(service.file(id)));
        assertEquals(new MediaProvenanceService().computeSha256(new byte[]{1,2,3,4,5,6}),ready.getSha256());
        assertEquals("application/octet-stream",ready.getMimeType());
    }
    @Test void guessedUploadCannotBeReadOrDeletedAndOwnerCanTerminate() throws Exception {
        UUID id=service.create(owner,10,null).getId();UUID stranger=UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(AppException.class,()->service.head(id,stranger)).getStatus());
        assertThrows(AppException.class,()->service.terminate(id,stranger));assertTrue(Files.exists(service.file(id)));
        service.terminate(id,owner);assertFalse(Files.exists(service.file(id)));
        assertEquals(HttpStatus.GONE,assertThrows(AppException.class,()->service.head(id,owner)).getStatus());
    }
    @Test void maliciousMetadataNeverControlsStoragePathOrInlineMime() throws Exception {
        String encoded=Base64.getEncoder().encodeToString("../../evil.html".getBytes());
        var upload=service.create(owner,0,"filename "+encoded+",filetype dGV4dC9odG1s");
        assertTrue(service.file(upload.getId()).startsWith(directory));assertFalse(upload.getFilename().contains("/"));
        assertEquals("application/octet-stream",upload.getMimeType());
        assertThrows(AppException.class,()->service.create(owner,-1,null));
        assertThrows(AppException.class,()->service.create(owner,1,"filename !!!"));
    }
}
