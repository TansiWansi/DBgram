package com.dbgram.repository;

import com.dbgram.domain.messaging.CassandraMessage;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends CassandraRepository<CassandraMessage, UUID> {

    @Query("SELECT * FROM messages_by_conversation WHERE convo_id = :convoId")
    List<CassandraMessage> findByConvoId(@Param("convoId") UUID convoId);

    @Query("SELECT * FROM messages_by_conversation WHERE convo_id = :convoId AND message_id = :messageId")
    Optional<CassandraMessage> findByConvoIdAndMessageId(@Param("convoId") UUID convoId,
                                                          @Param("messageId") UUID messageId);

    @Query("SELECT * FROM messages_by_conversation WHERE convo_id = :convoId LIMIT :limit")
    List<CassandraMessage> findByConversation(@Param("convoId") UUID convoId,
                                               @Param("limit") int limit);

    @Query("SELECT * FROM messages_by_conversation WHERE convo_id = :convoId AND message_id < :beforeId LIMIT :limit")
    List<CassandraMessage> findByConversationBefore(@Param("convoId") UUID convoId,
                                                     @Param("beforeId") UUID beforeId,
                                                     @Param("limit") int limit);
}
