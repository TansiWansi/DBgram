package com.dbgram.worker;

import com.dbgram.repository.CommunityRepository;
import com.dbgram.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Background worker for cleaning up expired temporal items.
 */
@Slf4j
@org.springframework.context.annotation.Profile("distributed")
@Service
@EnableScheduling
@RequiredArgsConstructor
public class EphemeralMessageService {

    private final CommunityRepository communityRepository;
    // Typically Cassandra TTL handles message expiration naturally.
    // However, we might want a cron job to clean up references if needed,
    // or handle soft-deletes that have surpassed the undo window.
    // For temporal communities, we definitely need a cron to clean them up 
    // if we don't rely entirely on Redis events.
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupExpiredCommunities() {
        var expired = communityRepository.findExpiredTemporalCommunities(Instant.now());
        if (!expired.isEmpty()) {
            log.info("Found {} expired temporal communities. Cleaning up...", expired.size());
            communityRepository.deleteAll(expired);
        }
    }
}
