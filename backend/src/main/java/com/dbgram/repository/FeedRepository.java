package com.dbgram.repository;

import com.dbgram.domain.feed.CassandraFeedEntry;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedRepository extends CassandraRepository<CassandraFeedEntry, UUID> {

    @Query("SELECT * FROM feed_by_user WHERE user_id = :userId LIMIT :limit")
    List<CassandraFeedEntry> findFeedForUser(@Param("userId") UUID userId,
                                              @Param("limit") int limit);

    @Query("SELECT * FROM feed_by_user WHERE user_id = :userId AND score < :beforeScore LIMIT :limit")
    List<CassandraFeedEntry> findFeedForUserBefore(@Param("userId") UUID userId,
                                                    @Param("beforeScore") double beforeScore,
                                                    @Param("limit") int limit);
}
