package com.dbgram.service;

import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryService {
    private final JdbcTemplate db;
    private final SocialService social;

    public List<Map<String, Object>> tray(UUID viewer) {
        // The tray contains your account and accepted follows. Public stories from
        // other accounts remain discoverable on their profiles, not as unsolicited rings.
        var groups = social.query("""
                SELECT a.user_id, MAX(p.published_at) AS latest_at, COUNT(*) AS story_count,
                    BOOL_OR(v.viewer_id IS NULL) AS has_unseen
                FROM posts p
                JOIN post_authors a ON a.post_id=p.id AND a.role='AUTHOR'
                LEFT JOIN story_views v ON v.post_id=p.id AND v.viewer_id=?
                WHERE p.post_type='STORY' AND p.status='PUBLISHED'
                    AND p.published_at>NOW()-INTERVAL '24 hours' AND check_post_access(p.id,?)
                    AND (a.user_id=? OR EXISTS (
                        SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=a.user_id
                    ))
                GROUP BY a.user_id
                ORDER BY (a.user_id=?) DESC, BOOL_OR(v.viewer_id IS NULL) DESC, MAX(p.published_at) DESC
                LIMIT 100
                """, viewer, viewer, viewer, viewer, viewer);
        for (var group : groups) {
            group.put("author", social.profile((UUID) group.get("userId"), viewer));
        }
        return groups;
    }

    public List<Map<String, Object>> byUser(UUID viewer, UUID author) {
        var account = social.profile(author, viewer);
        if (!Boolean.TRUE.equals(account.get("canViewPosts"))) {
            throw AppException.forbidden("Follow this private account to see their stories");
        }
        return social.query("""
                SELECT p.id, v.viewer_id IS NOT NULL AS seen
                FROM posts p JOIN post_authors a ON a.post_id=p.id AND a.role='AUTHOR'
                LEFT JOIN story_views v ON v.post_id=p.id AND v.viewer_id=?
                WHERE a.user_id=? AND p.post_type='STORY' AND p.status='PUBLISHED'
                    AND p.published_at>NOW()-INTERVAL '24 hours' AND check_post_access(p.id,?)
                ORDER BY p.published_at,p.id LIMIT 100
                """, viewer, author, viewer).stream().map(row -> {
                    var story = social.requirePost((UUID) row.get("id"), viewer);
                    story.put("seen", row.get("seen"));
                    if (Boolean.TRUE.equals(story.get("canEdit"))) {
                        story.put("viewerCount", db.queryForObject(
                                "SELECT COUNT(*) FROM story_views WHERE post_id=?", Long.class, row.get("id")));
                    }
                    return story;
                }).toList();
    }

    @Transactional
    public void viewed(UUID viewer, UUID id) {
        Map<String, Object> story = requireStory(viewer, id);
        if (!Boolean.TRUE.equals(story.get("canEdit"))) {
            // A replay or a second device records only one view per account.
            db.update("INSERT INTO story_views(post_id,viewer_id) VALUES(?,?) ON CONFLICT DO NOTHING", id, viewer);
        }
    }

    public List<Map<String, Object>> viewers(UUID viewer, UUID id) {
        Map<String, Object> story = requireStory(viewer, id);
        if (!Boolean.TRUE.equals(story.get("canEdit"))) {
            throw AppException.forbidden("Only the story author can see its viewers");
        }
        return social.query("""
                SELECT v.viewer_id,v.viewed_at FROM story_views v JOIN users u ON u.id=v.viewer_id
                WHERE v.post_id=? AND u.status='ACTIVE'
                    AND NOT EXISTS(SELECT 1 FROM blocked_users b
                        WHERE (b.blocker_id=? AND b.blocked_id=u.id)
                           OR (b.blocked_id=? AND b.blocker_id=u.id))
                ORDER BY v.viewed_at DESC LIMIT 100
                """, id, viewer, viewer).stream().map(row -> {
                    var person = social.profile((UUID) row.get("viewerId"), viewer);
                    person.put("viewedAt", row.get("viewedAt"));
                    return person;
                }).toList();
    }

    private Map<String, Object> requireStory(UUID viewer, UUID id) {
        var post = social.requirePost(id, viewer);
        if (!"STORY".equals(post.get("postType"))) {
            throw AppException.notFound("Story", id);
        }
        return post;
    }
}
