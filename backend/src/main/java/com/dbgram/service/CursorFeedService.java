package com.dbgram.service;

import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Chronological keyset paging and short-lived, immutable ranked candidate lists. */
@Service @RequiredArgsConstructor
public class CursorFeedService {
 private final JdbcTemplate db;
 private final SocialService social;
 private final ObjectMapper json;

 public record Cursor(String scope,Instant snapshot,Instant after,UUID afterId,UUID session,long ordinal) {}
 public record FeedPage(List<Map<String,Object>> items,String nextCursor,boolean hasMore) {}

    @Transactional
    public FeedPage page(UUID viewer, String mode, String token, int size, String postType, UUID community) {
        SocialService.size(size);
        if (!Set.of("discover", "following", "saved", "ranked").contains(mode))
            throw AppException.badRequest("Unknown feed mode");
        if (postType != null && !Set.of("STANDARD", "REEL", "STORY", "ARTICLE").contains(postType))
            throw AppException.badRequest("Unknown post type");
        String scope = viewer + ":" + mode + ":" + Objects.toString(postType, "") + ":" + Objects.toString(community, "");
        Cursor cursor = token == null || token.isBlank() ? new Cursor(scope, Instant.now(), null, null, null, 0) : decode(token, scope);
        return mode.equals("ranked") ? ranked(viewer, cursor, size, postType, community) : chronological(viewer, mode, cursor, size, postType, community);
    }

    private FeedPage chronological(UUID viewer, String mode, Cursor cursor, int size, String type, UUID community) {
        StringBuilder sql = new StringBuilder("SELECT p.id,p.published_at FROM posts p WHERE p.status='PUBLISHED' AND p.published_at<=? AND check_post_access(p.id,?)");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(cursor.snapshot()), viewer));
        if (cursor.after() != null) {
            sql.append(" AND (p.published_at,p.id)<(?,?)");
            args.add(Timestamp.from(cursor.after()));
            args.add(cursor.afterId());
        }
        if (mode.equals("following")) {
            sql.append(" AND EXISTS(SELECT 1 FROM post_authors a WHERE a.post_id=p.id AND (a.user_id=? OR EXISTS(SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=a.user_id)))");
            args.add(viewer);
            args.add(viewer);
        }
        if (mode.equals("saved")) {
            sql.append(" AND EXISTS(SELECT 1 FROM post_engagements e WHERE e.post_id=p.id AND e.user_id=? AND e.kind='save')");
            args.add(viewer);
        }
        filter(sql, args, type, community);
        sql.append(" ORDER BY p.published_at DESC,p.id DESC LIMIT ?");
        args.add(size + 1);
        var ids = social.query(sql.toString(), args.toArray());
        boolean more = ids.size() > size;
        if (more) ids = ids.subList(0, size);
        List<Map<String, Object>> posts = ids.stream().map(row -> social.requirePost((UUID) row.get("id"), viewer)).toList();
        String next = null;
        if (more) {
            var last = ids.getLast();
            next = encode(new Cursor(cursor.scope(), cursor.snapshot(), (Instant) last.get("publishedAt"), (UUID) last.get("id"), null, 0));
        }
        return new FeedPage(posts, next, more);
    }

    private FeedPage ranked(UUID viewer, Cursor cursor, int size, String type, UUID community) {
        UUID session = cursor.session();
        if (session == null) {
            StringBuilder sql = new StringBuilder("WITH candidates AS (SELECT p.* FROM posts p WHERE p.status='PUBLISHED' AND p.published_at<=? AND p.published_at>? AND check_post_access(p.id,?)");
            List<Object> args = new ArrayList<>(List.of(Timestamp.from(cursor.snapshot()), Timestamp.from(cursor.snapshot().minus(90, ChronoUnit.DAYS)), viewer));
            filter(sql, args, type, community);
            sql.append(" ORDER BY p.published_at DESC,p.id DESC LIMIT 5000) SELECT p.id, EXP(-0.01444*GREATEST(EXTRACT(EPOCH FROM (?::timestamptz-p.published_at))/3600.0,0)) * (CASE WHEN EXISTS(SELECT 1 FROM post_authors a JOIN follows f ON f.following_id=a.user_id WHERE a.post_id=p.id AND f.follower_id=?) THEN 2.0 ELSE 1.0 END) * (1.0 + (5.0*p.like_count + 8.0*p.comment_count + 3.0*p.save_count)/GREATEST(p.view_count,1)) AS rank_score FROM candidates p ORDER BY rank_score DESC,p.published_at DESC,p.id DESC LIMIT 500");
            args.add(Timestamp.from(cursor.snapshot()));
            args.add(viewer);
            var ids = social.query(sql.toString(), args.toArray()).stream().map(r -> r.get("id")).toList();
            session = UUID.randomUUID();
            db.update("INSERT INTO feed_sessions(id,user_id,scope,post_ids) VALUES(?,?,?,?::jsonb)", session, viewer, cursor.scope(), json(ids));
            // Bound stored snapshots for clients that repeatedly pull to refresh.
            db.update("DELETE FROM feed_sessions WHERE user_id=? AND id IN (SELECT id FROM feed_sessions WHERE user_id=? ORDER BY created_at DESC OFFSET 10)", viewer, viewer);
        } else {
            Integer count = db.queryForObject("SELECT count(*) FROM feed_sessions WHERE id=? AND user_id=? AND scope=? AND expires_at>NOW()", Integer.class, session, viewer, cursor.scope());
            if (count == null || count == 0) throw AppException.conflict("This ranked feed session expired. Refresh the feed.");
        }
        var ids = social.query("SELECT item.value::uuid AS id,item.ordinality FROM feed_sessions s CROSS JOIN LATERAL jsonb_array_elements_text(s.post_ids) WITH ORDINALITY AS item(value,ordinality) WHERE s.id=? AND s.user_id=? AND item.ordinality>? AND check_post_access(item.value::uuid,?) ORDER BY item.ordinality LIMIT ?", session, viewer, cursor.ordinal(), viewer, size + 1);
        boolean more = ids.size() > size;
        if (more) ids = ids.subList(0, size);
        List<Map<String, Object>> posts = ids.stream().map(r -> social.requirePost((UUID) r.get("id"), viewer)).toList();
        String next = more ? encode(new Cursor(cursor.scope(), cursor.snapshot(), null, null, session, ((Number) ids.getLast().get("ordinality")).longValue())) : null;
        return new FeedPage(posts, next, more);
    }

    private void filter(StringBuilder sql, List<Object> args, String type, UUID community) {
        if (type != null) {
            sql.append(" AND p.post_type=?");
            args.add(type);
        } else {
            sql.append(" AND p.post_type<>'STORY'");
        }
        if (community != null) {
            sql.append(" AND p.community_id=?");
            args.add(community);
        }
    }

    String encode(Cursor cursor) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json(cursor).getBytes(StandardCharsets.UTF_8));
    }

    Cursor decode(String token, String scope) {
        try {
            if (token.length() > 1200) throw new IllegalArgumentException();
            Cursor c = json.readValue(Base64.getUrlDecoder().decode(token), Cursor.class);
            if (!scope.equals(c.scope()) || c.snapshot() == null || c.snapshot().isAfter(Instant.now().plusSeconds(60)) || c.snapshot().isBefore(Instant.now().minus(7, ChronoUnit.DAYS)) || c.ordinal() < 0 || c.ordinal() > 500 || (c.after() == null) != (c.afterId() == null) || (c.after() != null && c.after().isAfter(c.snapshot())))
                throw new IllegalArgumentException();
            return c;
        } catch (Exception ex) {
            throw AppException.badRequest("Invalid or expired feed cursor");
        }
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw AppException.badRequest("Invalid feed cursor");
        }
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void expireSessions() {
        db.update("DELETE FROM feed_sessions WHERE expires_at<=NOW()");
    }
}


