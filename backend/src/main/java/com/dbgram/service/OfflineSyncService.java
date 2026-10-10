package com.dbgram.service;

import com.dbgram.util.AppException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Each client event is recorded in the same transaction as its interaction. */
@Service
public class OfflineSyncService {
    public enum EventType { LIKE, UNLIKE, SAVE, UNSAVE, VIEW }
    public record Event(UUID eventId, UUID postId, EventType type) {}
    private final JdbcTemplate db;
    private final SocialService social;
    private final TransactionTemplate transaction;

    public OfflineSyncService(JdbcTemplate db, SocialService social, PlatformTransactionManager manager) {
        this.db = db;
        this.social = social;
        this.transaction = new TransactionTemplate(manager);
    }

    public List<Map<String, Object>> sync(UUID actor, List<Event> events) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Event event : events) {
            try {
                results.add(transaction.execute(status -> apply(actor, event)));
            } catch (AppException error) {
                results.add(Map.of("eventId", event.eventId(), "status", "rejected", "message", error.getMessage()));
            }
        }
        return results;
    }

    private Map<String, Object> apply(UUID actor, Event event) {
        int inserted = db.update("INSERT INTO offline_events(user_id,event_id,post_id,event_type) VALUES (?,?,?,?) ON CONFLICT(user_id,event_id) DO NOTHING",
            actor, event.eventId(), event.postId(), event.type().name());
        if (inserted == 0) {
            Map<String, Object> prior = db.queryForMap("SELECT post_id,event_type FROM offline_events WHERE user_id=? AND event_id=?", actor, event.eventId());
            if (!event.postId().equals(prior.get("post_id")) || !event.type().name().equals(prior.get("event_type"))) {
                throw AppException.conflict("An event ID cannot be reused for another interaction");
            }
            return Map.of("eventId", event.eventId(), "status", "duplicate");
        }
        String kind = switch (event.type()) {
            case LIKE, UNLIKE -> "like";
            case SAVE, UNSAVE -> "save";
            case VIEW -> "view";
        };
        boolean enabled = event.type() != EventType.UNLIKE && event.type() != EventType.UNSAVE;
        social.engage(actor, event.postId(), kind, enabled);
        return Map.of("eventId", event.eventId(), "status", "applied");
    }
}
