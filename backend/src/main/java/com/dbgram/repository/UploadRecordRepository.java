package com.dbgram.repository;

import com.dbgram.domain.media.UploadRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface UploadRecordRepository extends JpaRepository<UploadRecord, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UploadRecord u where u.id=:id")
    Optional<UploadRecord> lockById(UUID id);
    long countByOwnerIdAndStatus(UUID ownerId, String status);
    List<UploadRecord> findTop100ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<UploadRecord> findTop100ByStatusAndExpiresAtBefore(String status, Instant now);
    List<UploadRecord> findTop5ByStatusAndTranscodeStatus(String status, String transcodeStatus);
    @Query("select coalesce(sum(u.uploadLength),0) from UploadRecord u where u.ownerId=:ownerId and u.status <> 'TERMINATED'")
    long reservedBytes(UUID ownerId);
}
