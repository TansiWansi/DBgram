package com.dbgram.repository;

import com.dbgram.domain.messaging.CassandraPreKeyBundle;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PreKeyBundleRepository extends CassandraRepository<CassandraPreKeyBundle, UUID> {

    List<CassandraPreKeyBundle> findByUserId(UUID userId);

    Optional<CassandraPreKeyBundle> findByUserIdAndDeviceId(UUID userId, Integer deviceId);
}
