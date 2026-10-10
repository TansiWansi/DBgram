package com.dbgram.domain.media;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Media asset tracking with SHA-256 provenance, transcoding status,
 * and TUS resumable upload tracking.
 */
@Entity
@Table(name = "media_assets")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    @Column(name = "original_url", nullable = false, length = 512)
    private String originalUrl;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> variants;

    // ── File Metadata ──

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "duration_ms")
    private Long durationMs;

    // ── Provenance ──

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "merkle_root", length = 64)
    private String merkleRoot;

    @Column(name = "blockchain_tx", length = 128)
    private String blockchainTx;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance_status", nullable = false, length = 20)
    @Builder.Default
    private ProvenanceStatus provenanceStatus = ProvenanceStatus.NONE;

    // ── Upload Tracking ──

    @Column(name = "tus_upload_id")
    private String tusUploadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // ── Enums ──

    public enum MediaType {
        IMAGE, VIDEO, AUDIO, DOCUMENT
    }

    public enum ProvenanceStatus {
        NONE, HASHED, BATCHED, ANCHORED
    }

    public enum UploadStatus {
        PENDING, UPLOADING, PROCESSING, READY, FAILED
    }
}
