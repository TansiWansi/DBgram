package com.dbgram.domain.messaging;

import lombok.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

/**
 * Signal Protocol PreKey bundle stored in Cassandra.
 * Used for X3DH key agreement — the server stores public keys only.
 */
@Table("prekey_bundles")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CassandraPreKeyBundle {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "device_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private Integer deviceId;

    /** Public identity key (Curve25519) */
    @Column("identity_key")
    private ByteBuffer identityKey;

    @Column("signed_prekey_id")
    private Integer signedPrekeyId;

    /** Signed pre-key public */
    @Column("signed_prekey")
    private ByteBuffer signedPrekey;

    /** Ed25519 signature over the signed pre-key */
    @Column("signed_prekey_sig")
    private ByteBuffer signedPrekeySig;

    @Column("registration_id")
    private Integer registrationId;

    @Column("updated_at")
    private Instant updatedAt;
}
