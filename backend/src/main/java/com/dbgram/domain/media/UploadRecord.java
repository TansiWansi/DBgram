package com.dbgram.domain.media;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="media_uploads") @Getter @Setter @NoArgsConstructor
public class UploadRecord {
    @Id private UUID id;
    @Column(nullable=false) private UUID ownerId;
    @Column(nullable=false) private long uploadLength;
    @Column(nullable=false) private long uploadOffset;
    @Column(nullable=false, length=255) private String filename;
    @Column(nullable=false, length=100) private String mimeType;
    @Column(length=4096) private String metadata;
    @Column(nullable=false, length=20) private String status = "UPLOADING";
    @Column(length=64) private String sha256;
    @Column(length=64) private String merkleRoot;
    @Column(columnDefinition="TEXT") private String leafHashes;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant expiresAt = Instant.now().plusSeconds(86400);
    @Column(length=20) private String transcodeStatus = "NOT_REQUESTED";
    @Column(length=512) private String transcodeError;
    @Version private long revision;
}
