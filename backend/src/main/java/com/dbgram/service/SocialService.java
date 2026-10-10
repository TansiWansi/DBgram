package com.dbgram.service;

import com.dbgram.util.AppException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * PostgreSQL is the source of truth for access control and every social
 * mutation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialService {
    private static final String USER_FIELDS = "u.id,u.username,u.display_name,u.bio,u.avatar_url,u.cover_url,u.is_verified,"
            + "u.is_business,u.is_private,u.follower_count,u.following_count,u.post_count,u.created_at";

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final MediaUploadService media;

    public List<Map<String, Object>> query(String sql, Object... args) {
        return db.query(sql, this::row, args);
    }

    private Map<String, Object> row(ResultSet result, int rowNumber) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        ResultSetMetaData metadata = result.getMetaData();

        // SQL uses snake_case; API responses use camelCase and JSON-compatible values.
        for (int column = 1; column <= metadata.getColumnCount(); column++) {
            String name = metadata.getColumnLabel(column);
            StringBuilder key = new StringBuilder();
            boolean uppercaseNext = false;
            for (char character : name.toCharArray()) {
                if (character == '_') {
                    uppercaseNext = true;
                } else {
                    key.append(uppercaseNext ? Character.toUpperCase(character) : character);
                    uppercaseNext = false;
                }
            }

            Object value = result.getObject(column);
            String columnType = metadata.getColumnTypeName(column);
            if (value instanceof Timestamp timestamp) {
                value = timestamp.toInstant();
            } else if (value instanceof java.sql.Array array) {
                value = Arrays.asList((Object[]) array.getArray());
            } else if (value != null && (columnType.equals("jsonb") || columnType.equals("json"))) {
                try {
                    value = json.readValue(value.toString(), Object.class);
                } catch (Exception exception) {
                    throw new SQLException("Invalid stored JSON", exception);
                }
            }
            values.put(key.toString(), value);
        }
        return values;
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = query(sql, args);
        if (rows.isEmpty()) {
            throw AppException.notFound("Resource", "requested");
        }
        return rows.getFirst();
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(" + sql + ")", Boolean.class, args));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw AppException.badRequest("Invalid JSON content");
        }
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception exception) {
            throw AppException.badRequest("Invalid ID");
        }
    }

    public static int size(int size) {
        if (size < 1 || size > 50) {
            throw AppException.badRequest("Page size must be between 1 and 50");
        }
        return size;
    }

    private static int offset(int page, int size) {
        if (page < 0 || page > 10000) {
            throw AppException.badRequest("Invalid page");
        }
        return page * size(size);
    }

    public static String text(Object value, String label, int max, boolean required) {
        if (value != null && !(value instanceof String)) {
            throw AppException.badRequest(label + " must be text");
        }
        String text = value == null ? "" : value.toString().trim();
        if (text.length() > max || (required && text.isBlank())) {
            throw AppException
                    .badRequest(label + " must contain " + (required ? "1" : "0") + "–" + max + " characters");
        }
        return text;
    }

    private static String choice(Object value, String fallback, String... options) {
        String selected = value == null ? fallback : value.toString().toUpperCase(Locale.ROOT);
        if (!List.of(options).contains(selected)) {
            throw AppException.badRequest("Unsupported value: " + selected);
        }
        return selected;
    }

    private void requireUnblocked(UUID first, UUID second) {
        if (exists("""
                SELECT 1 FROM blocked_users
                WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)
                """, first, second, second, first)) {
            throw AppException.forbidden("This interaction is unavailable");
        }
    }

    public Map<String, Object> profile(UUID id, UUID viewer) {
        requireUnblocked(viewer, id);
        Map<String, Object> user = one(
                "SELECT " + USER_FIELDS + " FROM users u WHERE u.id=? AND u.status='ACTIVE'", id);
        user.put("isCloseFriend", exists(
                "SELECT 1 FROM follows WHERE follower_id=? AND following_id=? AND is_close_friend", viewer, id));
        user.put("isFollowing", exists(
                "SELECT 1 FROM follows WHERE follower_id=? AND following_id=?", viewer, id));
        user.put("followRequested", exists(
                "SELECT 1 FROM follow_requests WHERE follower_id=? AND following_id=?", viewer, id));
        user.put("canViewPosts", Boolean.TRUE.equals(db.queryForObject(
                "SELECT can_view_account_content(?,?)", Boolean.class, id, viewer)));
        user.put("hasStory", exists("""
                SELECT 1 FROM posts p JOIN post_authors a ON a.post_id=p.id
                WHERE a.user_id=? AND p.post_type='STORY' AND p.status='PUBLISHED'
                  AND p.published_at>NOW()-INTERVAL '24 hours' AND check_post_access(p.id,?)
                """, id, viewer));
        signProfile(user, viewer);
        return user;
    }

    public Map<String, Object> profile(String username, UUID viewer) {
        if (username.matches("[0-9a-fA-F-]{36}")) {
            return profile(uuid(username), viewer);
        }
        UUID id = uuid(one(
                "SELECT id FROM users WHERE lower(username)=lower(?) AND status='ACTIVE'", username).get("id"));
        return profile(id, viewer);
    }

    public List<Map<String, Object>> users(UUID viewer, String search, int limit) {
        String pattern = "%" + text(search, "Search", 100, false)
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
        var users = query("SELECT " + USER_FIELDS + """
                , EXISTS(
                    SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=u.id
                ) AS is_following,
                EXISTS(
                    SELECT 1 FROM follow_requests r WHERE r.follower_id=? AND r.following_id=u.id
                ) AS follow_requested
                FROM users u
                WHERE u.status='ACTIVE' AND u.id<>?
                  AND (u.username ILIKE ? OR u.display_name ILIKE ?)
                  AND NOT EXISTS(
                      SELECT 1 FROM blocked_users b
                      WHERE (b.blocker_id=? AND b.blocked_id=u.id)
                         OR (b.blocked_id=? AND b.blocker_id=u.id)
                  )
                ORDER BY u.follower_count DESC,u.id LIMIT ?
                """, viewer, viewer, viewer, pattern, pattern, viewer, viewer, size(limit));
        users.forEach(user -> signProfile(user, viewer));
        return users;
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID viewer, Map<String, Object> body) {
        Map<String, Object> previous = one("SELECT " + USER_FIELDS + " FROM users u WHERE u.id=?", viewer);
        String display = body.containsKey("displayName")
                ? text(body.get("displayName"), "Display name", 100, true)
                : (String) previous.get("displayName");
        String bio = body.containsKey("bio")
                ? text(body.get("bio"), "Bio", 1000, false)
                : (String) previous.get("bio");
        String avatar = body.containsKey("avatarUrl")
                ? safeUrl(body.get("avatarUrl"))
                : (String) previous.get("avatarUrl");
        String cover = body.containsKey("coverUrl")
                ? safeUrl(body.get("coverUrl"))
                : (String) previous.get("coverUrl");

        avatar = ownedMediaUrl(avatar, viewer);
        cover = ownedMediaUrl(cover, viewer);
        db.update("UPDATE users SET display_name=?,bio=?,avatar_url=?,cover_url=? WHERE id=?",
                display, bio, avatar, cover, viewer);
        return profile(viewer, viewer);
    }

    public static String safeUrl(Object value) {
        String url = text(value, "URL", 512, false);
        if (url.isEmpty() || url.matches("/api/media/[a-zA-Z0-9/?=&_.%-]+")) {
            return url;
        }

        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean local = "http".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(Locale.ROOT));
            if ((https || local) && host != null && uri.getUserInfo() == null
                    && uri.getFragment() == null && !url.contains("\\")) {
                return url;
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed URIs use the same validation response as unsupported schemes.
        }
        throw AppException.badRequest("Use an HTTPS or uploaded media URL");
    }

    @Transactional
    public Map<String, Object> follow(UUID viewer, UUID target, boolean enabled) {
        if (viewer.equals(target)) {
            throw AppException.badRequest("You cannot follow yourself");
        }
        // Lock before checking privacy so a simultaneous privacy change cannot
        // accidentally turn a pending request into an accepted relationship.
        query("SELECT id FROM users WHERE id IN (?,?) ORDER BY id FOR UPDATE", viewer, target);
        Map<String, Object> account = profile(target, viewer);
        if (enabled && Boolean.TRUE.equals(account.get("isFollowing"))) {
            return account;
        }
        if (enabled && Boolean.TRUE.equals(account.get("isPrivate"))) {
            int requested = db.update("""
                    INSERT INTO follow_requests(follower_id,following_id)
                    VALUES(?,?) ON CONFLICT DO NOTHING
                    """, viewer, target);
            if (requested > 0) {
                notify(target, viewer, null, "FOLLOW_REQUEST");
            }
            return profile(target, viewer);
        }

        clearFollowRequest(viewer, target);
        int changed = enabled
                ? db.update("INSERT INTO follows(follower_id,following_id) VALUES(?,?) ON CONFLICT DO NOTHING", viewer,
                        target)
                : db.update("DELETE FROM follows WHERE follower_id=? AND following_id=?", viewer, target);
        if (changed > 0) {
            // Replayed requests leave counters unchanged because no relationship row
            // changed.
            int delta = enabled ? 1 : -1;
            db.update("UPDATE users SET following_count=GREATEST(0,following_count+?) WHERE id=?", delta, viewer);
            db.update("UPDATE users SET follower_count=GREATEST(0,follower_count+?) WHERE id=?", delta, target);
            if (enabled) {
                notify(target, viewer, null, "FOLLOW");
            }
        }
        return profile(target, viewer);
    }

    @Transactional
    public Map<String, Object> accountPrivacy(UUID viewer, Boolean isPrivate) {
        if (isPrivate == null) {
            throw AppException.badRequest("Choose public or private account visibility");
        }
        db.update("UPDATE users SET is_private=? WHERE id=?", isPrivate, viewer);
        // Existing followers remain approved. Pending requests still require an
        // explicit decision; opening an account does not silently create follows.
        return profile(viewer, viewer);
    }

    public List<Map<String, Object>> followRequests(UUID viewer) {
        return query("""
                SELECT r.follower_id FROM follow_requests r JOIN users u ON u.id=r.follower_id
                WHERE r.following_id=? AND u.status='ACTIVE'
                  AND NOT EXISTS(SELECT 1 FROM blocked_users b
                      WHERE (b.blocker_id=? AND b.blocked_id=u.id)
                         OR (b.blocked_id=? AND b.blocker_id=u.id))
                ORDER BY r.created_at,r.follower_id LIMIT 100
                """, viewer, viewer, viewer).stream()
                .map(row -> profile(uuid(row.get("followerId")), viewer))
                .toList();
    }

    @Transactional
    public void respondFollowRequest(UUID owner, UUID requester, boolean accept) {
        query("SELECT id FROM users WHERE id IN (?,?) ORDER BY id FOR UPDATE", owner, requester);
        requireUnblocked(owner, requester);
        int removed = clearFollowRequest(requester, owner);
        if (removed == 0 || !accept) {
            return;
        }
        int inserted = db.update("""
                INSERT INTO follows(follower_id,following_id) VALUES(?,?) ON CONFLICT DO NOTHING
                """, requester, owner);
        if (inserted > 0) {
            db.update("UPDATE users SET following_count=following_count+1 WHERE id=?", requester);
            db.update("UPDATE users SET follower_count=follower_count+1 WHERE id=?", owner);
            notify(requester, owner, null, "FOLLOW_ACCEPTED");
        }
    }

    private int clearFollowRequest(UUID requester, UUID owner) {
        db.update("DELETE FROM notifications WHERE actor_id=? AND user_id=? AND type='FOLLOW_REQUEST'",
                requester, owner);
        return db.update("DELETE FROM follow_requests WHERE follower_id=? AND following_id=?", requester, owner);
    }

    public List<Map<String, Object>> connections(UUID target, UUID viewer, boolean followers) {
        Map<String, Object> account = profile(target, viewer);
        if (!Boolean.TRUE.equals(account.get("canViewPosts"))) {
            throw AppException.forbidden("Follow this private account to see its connections");
        }
        String join = followers ? "f.follower_id" : "f.following_id";
        String where = followers ? "f.following_id" : "f.follower_id";
        var people = query(
                "SELECT " + USER_FIELDS + " FROM users u JOIN follows f ON u.id=" + join
                        + " WHERE " + where + "=? AND u.status='ACTIVE'"
                        + " AND NOT EXISTS(SELECT 1 FROM blocked_users b WHERE"
                        + " (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocked_id=? AND b.blocker_id=u.id))"
                        + " ORDER BY f.created_at DESC LIMIT 50",
                target, viewer, viewer);
        people.forEach(person -> signProfile(person, viewer));
        return people;
    }

    @Transactional
    public void closeFriend(UUID viewer, UUID target, boolean enabled) {
        int changed = db.update(
                "UPDATE follows SET is_close_friend=? WHERE follower_id=? AND following_id=?", enabled, viewer, target);
        if (changed == 0) {
            throw AppException.badRequest("Follow this person before adding them to close friends");
        }
    }

    @Transactional
    public void block(UUID viewer, UUID target, boolean enabled) {
        if (viewer.equals(target)) {
            throw AppException.badRequest("Cannot block yourself");
        }
        if (enabled && exists("SELECT 1 FROM blocked_users WHERE blocker_id=? AND blocked_id=?", viewer, target)) {
            return;
        }
        if (!enabled) {
            db.update("DELETE FROM blocked_users WHERE blocker_id=? AND blocked_id=?", viewer, target);
            return;
        }

        one("SELECT id FROM users WHERE id=?", target);
        query("SELECT id FROM users WHERE id IN (?,?) ORDER BY id FOR UPDATE", viewer, target);
        clearFollowRequest(viewer, target);
        clearFollowRequest(target, viewer);
        int outgoing = db.update("DELETE FROM follows WHERE follower_id=? AND following_id=?", viewer, target);
        int incoming = db.update("DELETE FROM follows WHERE follower_id=? AND following_id=?", target, viewer);
        db.update("""
                UPDATE users SET following_count=GREATEST(0,following_count-?),
                    follower_count=GREATEST(0,follower_count-?) WHERE id=?
                """, outgoing, incoming, viewer);
        db.update("""
                UPDATE users SET following_count=GREATEST(0,following_count-?),
                    follower_count=GREATEST(0,follower_count-?) WHERE id=?
                """, incoming, outgoing, target);
        db.update("INSERT INTO blocked_users(blocker_id,blocked_id) VALUES(?,?) ON CONFLICT DO NOTHING", viewer,
                target);
    }

    public Map<String, Object> requirePost(UUID postId, UUID viewer) {
        var post = one("SELECT p.* FROM posts p WHERE p.id=? AND check_post_access(p.id,?)", postId, viewer);
        return enrichPost(post, viewer);
    }

    private Map<String, Object> enrichPost(Map<String, Object> post, UUID viewer) {
        UUID id = uuid(post.get("id"));
        var authors = query("""
                SELECT u.id,u.id AS user_id,u.username,u.display_name,u.avatar_url,
                    u.is_verified,pa.role,pa.contribution_pct
                FROM post_authors pa JOIN users u ON u.id=pa.user_id
                WHERE pa.post_id=?
                ORDER BY CASE pa.role WHEN 'AUTHOR' THEN 0 ELSE 1 END,pa.added_at
                """, id);
        post.put("authors", authors);
        post.put("author", authors.isEmpty() ? null : authors.getFirst());
        authors.forEach(author -> signProfile(author, viewer));
        post.put("blocks", signMedia(post.get("blocks"), viewer));
        post.put("isLiked", exists(
                "SELECT 1 FROM post_engagements WHERE post_id=? AND user_id=? AND kind='like'", id, viewer));
        post.put("isSaved", exists(
                "SELECT 1 FROM post_engagements WHERE post_id=? AND user_id=? AND kind='save'", id, viewer));

        boolean canEdit = authors.stream().anyMatch(author -> viewer.equals(author.get("id")));
        boolean canManageAudience = authors.stream()
                .anyMatch(author -> viewer.equals(author.get("id")) && "AUTHOR".equals(author.get("role")));
        post.put("canEdit", canEdit);
        if ("STORY".equals(post.get("postType"))) {
            post.put("expiresAt", ((Instant) post.get("publishedAt")).plusSeconds(86400));
        }
        post.put("canManageAudience", canManageAudience);
        if (canEdit) {
            // Audience membership is editor metadata, not part of the public post response.
            post.put("audience", Map.of(
                    "allowedListIds", db.queryForList(
                            "SELECT target_list_id FROM post_acl WHERE post_id=? AND rule_type='ALLOW_LIST'",
                            UUID.class, id),
                    "allowedUserIds", db.queryForList(
                            "SELECT target_user_id FROM post_acl WHERE post_id=? AND rule_type='ALLOW_USER'",
                            UUID.class, id)));
        }
        post.put("pollResults", query("""
                SELECT block_index,option_index,count(*) AS votes FROM poll_votes
                WHERE post_id=? GROUP BY block_index,option_index ORDER BY block_index,option_index
                """, id));
        post.put("myVotes", query(
                "SELECT block_index,option_index FROM poll_votes WHERE post_id=? AND user_id=?", id, viewer));
        if (post.get("communityId") != null) {
            post.put("community", one(
                    "SELECT id,name,slug,avatar_url FROM communities WHERE id=?", post.get("communityId")));
        }
        return post;
    }

    public List<Map<String, Object>> posts(
            UUID viewer, String mode, int page, int limit, UUID author, UUID community, String search, String type) {
        int start = offset(page, limit);
        StringBuilder sql = new StringBuilder(
                "SELECT p.* FROM posts p WHERE p.status='PUBLISHED' AND check_post_access(p.id,?)");
        List<Object> args = new ArrayList<>();
        args.add(viewer);

        if ("following".equals(mode)) {
            sql.append("""
                     AND EXISTS(
                        SELECT 1 FROM post_authors pa WHERE pa.post_id=p.id
                        AND (pa.user_id=? OR EXISTS(
                            SELECT 1 FROM follows f WHERE f.follower_id=? AND f.following_id=pa.user_id
                        ))
                    )
                    """);
            args.add(viewer);
            args.add(viewer);
        }
        if ("saved".equals(mode)) {
            sql.append("""
                     AND EXISTS(
                        SELECT 1 FROM post_engagements e WHERE e.post_id=p.id AND e.user_id=? AND e.kind='save'
                    )
                    """);
            args.add(viewer);
        }
        if (author != null) {
            sql.append(" AND EXISTS(SELECT 1 FROM post_authors pa WHERE pa.post_id=p.id AND pa.user_id=?)");
            args.add(author);
        }
        if (community != null) {
            sql.append(" AND p.community_id=?");
            args.add(community);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (p.text_content ILIKE ? OR EXISTS(SELECT 1 FROM unnest(p.hashtags) h WHERE h ILIKE ?))");
            String pattern = "%" + text(search, "Search", 100, false)
                    .replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND p.post_type=?");
            args.add(choice(type, "STANDARD", "STANDARD", "REEL", "STORY", "ARTICLE"));
        } else {
            sql.append(" AND p.post_type<>'STORY'");
        }
        sql.append(" ORDER BY p.published_at DESC,p.id DESC LIMIT ? OFFSET ?");
        args.add(size(limit));
        args.add(start);
        return query(sql.toString(), args.toArray()).stream()
                .map(post -> enrichPost(post, viewer))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> validateBlocks(Object raw) {
        if (!(raw instanceof List<?> blocks) || blocks.isEmpty() || blocks.size() > 30) {
            throw AppException.badRequest("A post needs 1–30 content blocks");
        }
        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> block) || !(block.get("data") instanceof Map<?, ?> data)) {
                throw AppException.badRequest("Each block needs type and data");
            }
            String type = String.valueOf(block.get("type"));
            switch (type) {
                case "text", "code" -> text(data.get("content"), "Block text", 20000, true);
                case "carousel" -> {
                    Object media = data.containsKey("media_urls") ? data.get("media_urls") : data.get("mediaUrls");
                    if (!(media instanceof List<?> urls) || urls.isEmpty() || urls.size() > 10) {
                        throw AppException.badRequest("Carousel needs 1–10 media URLs");
                    }
                    for (Object url : urls) {
                        safeUrl(url);
                    }
                }
                case "image", "video", "audio" -> {
                    if (safeUrl(data.get("url")).isBlank()) {
                        throw AppException.badRequest("Media URL is required");
                    }
                }
                case "embed" -> {
                    if (safeUrl(data.get("url")).isBlank()) {
                        throw AppException.badRequest("Embed URL is required");
                    }
                }
                case "poll" -> {
                    text(data.get("question"), "Poll question", 500, true);
                    if (!(data.get("options") instanceof List<?> options)
                            || options.size() < 2 || options.size() > 10) {
                        throw AppException.badRequest("Poll needs 2–10 options");
                    }
                    for (Object option : options) {
                        Object label = option instanceof Map<?, ?> entry ? entry.get("text") : option;
                        text(label, "Poll option", 200, true);
                    }
                }
                default -> throw AppException.badRequest("Unsupported content block: " + type);
            }
        }
        return (List<Map<String, Object>>) raw;
    }

    private String extractText(List<Map<String, Object>> blocks) {
        return blocks.stream()
                .map(block -> (Map<?, ?>) block.get("data"))
                .map(data -> Objects.toString(data.get("content"), Objects.toString(data.get("question"), "")))
                .reduce("", (previous, next) -> previous + " " + next)
                .trim();
    }

    private String ownedMediaUrl(String url, UUID owner) {
        return ownedMediaUrl(url, owner, Set.of());
    }

    private String ownedMediaUrl(String url, UUID owner, Set<String> existing) {
        if (url == null || !url.startsWith("/api/media/")) {
            return url;
        }
        String canonical = url.split("\\?", 2)[0];
        UUID id = uuid(canonical.substring("/api/media/".length()));

        // Coauthors may retain a post's attachments, but cannot attach another user's
        // new upload.
        if (!exists("""
                SELECT 1 FROM media_assets
                WHERE id=? AND upload_status='READY' AND (uploader_id=? OR ?)
                """, id, owner, existing.contains(canonical))) {
            throw AppException.forbidden("Attach only your own completed uploads");
        }
        return canonical;
    }

    private void collectMedia(Object value, Set<String> urls) {
        if (value instanceof String url && url.startsWith("/api/media/")) {
            urls.add(url.split("\\?", 2)[0]);
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> collectMedia(child, urls));
        } else if (value instanceof List<?> list) {
            list.forEach(child -> collectMedia(child, urls));
        }
    }

    private List<Map<String, Object>> normalizeMedia(List<Map<String, Object>> blocks, UUID owner) {
        return normalizeMedia(blocks, owner, Set.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeMedia(
            List<Map<String, Object>> blocks, UUID owner, Set<String> existing) {
        // Copy before normalization so the previous content remains suitable for
        // version history.
        List<Map<String, Object>> copy = json.convertValue(blocks, new TypeReference<>() {
        });
        for (var block : copy) {
            Map<String, Object> data = (Map<String, Object>) block.get("data");
            String type = String.valueOf(block.get("type"));
            if (List.of("image", "video", "audio", "embed").contains(type)) {
                data.put("url", ownedMediaUrl((String) data.get("url"), owner, existing));
            }
            if (type.equals("carousel")) {
                String key = data.containsKey("media_urls") ? "media_urls" : "mediaUrls";
                List<?> urls = (List<?>) data.get(key);
                data.put(key, urls.stream()
                        .map(url -> ownedMediaUrl(String.valueOf(url), owner, existing))
                        .toList());
            }
            if (data.get("mediaId") != null) {
                ownedMediaUrl("/api/media/" + uuid(data.get("mediaId")), owner, existing);
            }
        }
        return copy;
    }

    private Object signMedia(Object value, UUID viewer) {
        if (value instanceof String url && url.startsWith("/api/media/")) {
            try {
                UUID id = uuid(url.split("\\?", 2)[0].substring("/api/media/".length()));
                return media.metadata(id, viewer).get("url");
            } catch (AppException exception) {
                // Removed or inaccessible media must not receive a new signed URL.
                return null;
            }
        }
        if (value instanceof List<?> list) {
            return list.stream().map(child -> signMedia(child, viewer)).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> signed = new LinkedHashMap<>();
            map.forEach((key, child) -> signed.put(key.toString(), signMedia(child, viewer)));
            return signed;
        }
        return value;
    }

    private void signProfile(Map<String, Object> profile, UUID viewer) {
        if (profile.get("avatarUrl") != null) {
            profile.put("avatarUrl", signMedia(profile.get("avatarUrl"), viewer));
        }
        if (profile.get("coverUrl") != null) {
            profile.put("coverUrl", signMedia(profile.get("coverUrl"), viewer));
        }
    }

    private void hashtags(UUID post, List<Map<String, Object>> blocks, Object explicit) {
        Set<String> tags = new LinkedHashSet<>();
        var matcher = java.util.regex.Pattern.compile("#([\\p{L}\\p{N}_]{1,50})").matcher(extractText(blocks));
        while (matcher.find()) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (explicit instanceof List<?> list) {
            for (Object tag : list) {
                String normalized = text(tag, "Hashtag", 50, true)
                        .replaceFirst("^#", "")
                        .toLowerCase(Locale.ROOT);
                if (!normalized.matches("[\\p{L}\\p{N}_]+")) {
                    throw AppException.badRequest("Invalid hashtag");
                }
                tags.add(normalized);
            }
        }
        if (tags.size() > 30) {
            throw AppException.badRequest("Maximum 30 hashtags");
        }
        db.update("UPDATE posts SET hashtags=ARRAY(SELECT jsonb_array_elements_text(?::jsonb)) WHERE id=?",
                encode(tags), post);
    }

    @Transactional
    public Map<String, Object> createPost(UUID viewer, Map<String, Object> body) {
        var blocks = normalizeMedia(validateBlocks(body.get("blocks")), viewer);
        String postType = choice(body.get("postType"), "STANDARD", "STANDARD", "REEL", "STORY", "ARTICLE");
        String visibility = choice(body.get("visibility"), "PUBLIC",
                "PUBLIC", "FOLLOWERS_ONLY", "CLOSE_FRIENDS", "COMMUNITY_ONLY", "CUSTOM", "PRIVATE");
        UUID community = body.get("communityId") == null ? null : uuid(body.get("communityId"));
        validateStory(blocks, postType, community);
        if (community != null && !Boolean.TRUE.equals(db.queryForObject(
                "SELECT active_community_member(?,?)", Boolean.class, community, viewer))) {
            throw AppException.forbidden("Active community membership is required to publish here");
        }
        if (visibility.equals("COMMUNITY_ONLY") && community == null) {
            throw AppException.badRequest("Community visibility requires a community");
        }

        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO posts(id,blocks,text_content,post_type,visibility,community_id,published_at)
                VALUES(?,?::jsonb,?,?,?,?,NOW())
                """, id, encode(blocks), extractText(blocks), postType, visibility, community);
        db.update("INSERT INTO post_authors(post_id,user_id,role,contribution_pct) VALUES(?,?,'AUTHOR',100)",
                id, viewer);
        if (!postType.equals("STORY")) {
            db.update("UPDATE users SET post_count=post_count+1 WHERE id=?", viewer);
        }
        if (community != null) {
            db.update("UPDATE communities SET post_count=post_count+1 WHERE id=?", community);
        }
        setAudience(id, viewer, body);
        hashtags(id, blocks, body.get("hashtags"));
        return requirePost(id, viewer);
    }

    public static void validateStory(List<Map<String, Object>> blocks, String type, UUID community) {
        if (!"STORY".equals(type)) {
            return;
        }
        if (community != null || blocks.size() > 2) {
            throw AppException.badRequest("A story contains one photo or video and an optional caption, or just text");
        }
        int mediaCount = 0;
        int textCount = 0;
        for (Map<String, Object> block : blocks) {
            String blockType = String.valueOf(block.get("type"));
            Map<?, ?> data = (Map<?, ?>) block.get("data");
            if (blockType.equals("text")) {
                text(data.get("content"), "Story text", 2000, true);
                textCount++;
            } else if (blockType.equals("image") || blockType.equals("video")) {
                String url = String.valueOf(data.get("url"));
                if (!url.matches("/api/media/[0-9a-fA-F-]{36}")) {
                    throw AppException.badRequest("Upload story media to DBgram so its visibility can be protected");
                }
                mediaCount++;
            } else {
                throw AppException.badRequest("Stories support photos, videos, and text");
            }
        }
        if (mediaCount > 1 || textCount > 1 || mediaCount + textCount == 0) {
            throw AppException.badRequest("Share one photo or video with an optional caption, or a text story");
        }
    }

    private void setAudience(UUID post, UUID viewer, Map<String, Object> body) {
        if (body.containsKey("allowedUserIds") && !(body.get("allowedUserIds") instanceof List<?>)) {
            throw AppException.badRequest("Allowed users must be a list");
        }
        if (body.containsKey("allowedListIds") && !(body.get("allowedListIds") instanceof List<?>)) {
            throw AppException.badRequest("Allowed audience lists must be a list");
        }
        if (body.get("allowedUserIds") instanceof List<?> users) {
            if (users.size() > 100) {
                throw AppException.badRequest("Audience cannot exceed 100 people");
            }
            for (Object value : users) {
                UUID id = uuid(value);
                profile(id, viewer);
                db.update("INSERT INTO post_acl(post_id,rule_type,target_user_id) VALUES(?,'ALLOW_USER',?)", post, id);
            }
        }
        if (body.get("allowedListIds") instanceof List<?> lists) {
            if (lists.size() > 20) {
                throw AppException.badRequest("Audience cannot exceed 20 lists");
            }
            for (Object value : lists) {
                UUID id = uuid(value);
                if (!exists("SELECT 1 FROM acl_lists WHERE id=? AND owner_id=?", id, viewer)) {
                    throw AppException.forbidden("Audience list belongs to another user");
                }
                db.update("INSERT INTO post_acl(post_id,rule_type,target_list_id) VALUES(?,'ALLOW_LIST',?)", post, id);
            }
        }
    }

    private Map<String, Object> lockAuthor(UUID post, UUID user) {
        // Serializes edits, invitations and contribution changes for the same post.
        var current = one("SELECT p.* FROM posts p WHERE p.id=? AND p.status<>'REMOVED' FOR UPDATE", post);
        if (!exists("SELECT 1 FROM post_authors WHERE post_id=? AND user_id=?", post, user)) {
            throw AppException.forbidden("Only an author can edit this post");
        }
        return current;
    }

    private boolean isOriginalAuthor(UUID post, UUID user) {
        return exists("SELECT 1 FROM post_authors WHERE post_id=? AND user_id=? AND role='AUTHOR'", post, user);
    }

    @Transactional
    public Map<String, Object> inviteAuthor(UUID viewer, UUID post, UUID invitee, int percent) {
        if ("STORY".equals(requirePost(post, viewer).get("postType"))) {
            throw AppException.badRequest("Stories have a single author");
        }
        lockAuthor(post, viewer);
        if (!isOriginalAuthor(post, viewer)) {
            throw AppException.forbidden("Only the original author can invite collaborators");
        }
        profile(invitee, viewer);
        if (viewer.equals(invitee) || percent < 1 || percent > 99) {
            throw AppException.badRequest("Choose another author and a contribution of 1–99 percent");
        }
        if (exists("SELECT 1 FROM post_authors WHERE post_id=? AND user_id=?", post, invitee)) {
            throw AppException.conflict("This person is already an author");
        }

        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO post_author_invitations(id,post_id,inviter_id,invitee_id,contribution_pct)
                VALUES(?,?,?,?,?) ON CONFLICT(post_id,invitee_id)
                DO UPDATE SET contribution_pct=EXCLUDED.contribution_pct,status='PENDING'
                """, id, post, viewer, invitee, percent);
        notify(invitee, viewer, null, "COLLABORATION");
        return one("SELECT * FROM post_author_invitations WHERE post_id=? AND invitee_id=?", post, invitee);
    }

    public List<Map<String, Object>> invitations(UUID viewer) {
        return query("""
                SELECT i.*,p.text_content AS preview
                FROM post_author_invitations i JOIN posts p ON p.id=i.post_id
                WHERE i.invitee_id=? AND i.status='PENDING' AND p.status='PUBLISHED'
                ORDER BY i.created_at DESC LIMIT 100
                """, viewer);
    }

    @Transactional
    public Map<String, Object> respondInvitation(UUID viewer, UUID invite, boolean accept) {
        var invitation = one("SELECT * FROM post_author_invitations WHERE id=? AND invitee_id=?", invite, viewer);
        UUID post = uuid(invitation.get("postId"));
        one("SELECT id FROM posts WHERE id=? AND status='PUBLISHED' FOR UPDATE", post);
        invitation = one("SELECT * FROM post_author_invitations WHERE id=? FOR UPDATE", invite);
        if (!"PENDING".equals(invitation.get("status"))) {
            throw AppException.conflict("Invitation has already been answered");
        }

        if (accept) {
            UUID original = uuid(invitation.get("inviterId"));
            requireUnblocked(viewer, original);
            int percent = ((Number) invitation.get("contributionPct")).intValue();

            // Transfer the share while holding the post lock; the total remains 100
            // percent.
            int changed = db.update("""
                    UPDATE post_authors SET contribution_pct=contribution_pct-?
                    WHERE post_id=? AND user_id=? AND role='AUTHOR' AND contribution_pct>?
                    """, percent, post, original, percent);
            if (changed == 0) {
                throw AppException.conflict("The original author must allocate enough contribution first");
            }
            db.update("INSERT INTO post_authors(post_id,user_id,role,contribution_pct) VALUES(?,?,'CO_AUTHOR',?)",
                    post, viewer, percent);
            db.update("UPDATE users SET post_count=post_count+1 WHERE id=?", viewer);
        }
        db.update("UPDATE post_author_invitations SET status=? WHERE id=?", accept ? "ACCEPTED" : "DECLINED", invite);
        return Map.of("accepted", accept, "postId", post);
    }

    @Transactional
    public Map<String, Object> contributions(UUID viewer, UUID post, Map<String, Integer> shares) {
        lockAuthor(post, viewer);
        if (!isOriginalAuthor(post, viewer)) {
            throw AppException.forbidden("Only the original author can allocate contributions");
        }
        var authors = query("SELECT user_id FROM post_authors WHERE post_id=?", post);
        if (shares.size() != authors.size()
                || shares.values().stream().anyMatch(value -> value == null || value < 0 || value > 100)
                || shares.values().stream().mapToInt(Integer::intValue).sum() != 100) {
            throw AppException.badRequest("Include every accepted author with contributions totaling 100 percent");
        }

        for (var author : authors) {
            String id = author.get("userId").toString();
            if (!shares.containsKey(id)) {
                throw AppException.badRequest("Unknown author allocation");
            }
            db.update("UPDATE post_authors SET contribution_pct=? WHERE post_id=? AND user_id=?",
                    shares.get(id), post, uuid(id));
        }
        return requirePost(post, viewer);
    }

    @Transactional
    public Map<String, Object> updatePost(UUID viewer, UUID id, Map<String, Object> body) {
        var previous = lockAuthor(id, viewer);
        Set<String> existingMedia = new HashSet<>();
        collectMedia(previous.get("blocks"), existingMedia);
        var blocks = normalizeMedia(validateBlocks(body.get("blocks")), viewer, existingMedia);
        String visibility = choice(body.get("visibility"), previous.get("visibility").toString(),
                "PUBLIC", "FOLLOWERS_ONLY", "CLOSE_FRIENDS", "COMMUNITY_ONLY", "CUSTOM", "PRIVATE");
        String type = choice(body.get("postType"), previous.get("postType").toString(),
                "STANDARD", "REEL", "STORY", "ARTICLE");
        UUID community = (UUID) previous.get("communityId");
        if (body.containsKey("communityId")) {
            community = body.get("communityId") == null ? null : uuid(body.get("communityId"));
        }
        if ("STORY".equals(type) != "STORY".equals(previous.get("postType"))) {
            throw AppException.badRequest("Publish a new story instead of converting an existing post");
        }
        validateStory(blocks, type, community);

        boolean audienceChange = body.containsKey("allowedListIds") || body.containsKey("allowedUserIds");
        boolean metadataChange = !visibility.equals(previous.get("visibility"))
                || !type.equals(previous.get("postType"))
                || !Objects.equals(community, previous.get("communityId"));
        if ((audienceChange || metadataChange) && !isOriginalAuthor(id, viewer)) {
            throw AppException.forbidden("Only the original author can change the audience and publication settings");
        }
        if (visibility.equals("COMMUNITY_ONLY") && community == null) {
            throw AppException.badRequest("Community visibility requires a community");
        }
        if (community != null && !Objects.equals(community, previous.get("communityId"))
                && !exists("SELECT 1 FROM communities WHERE id=? AND active_community_member(id,?)", community,
                        viewer)) {
            throw AppException.forbidden("Join this community before publishing to it");
        }
        if (audienceChange) {
            db.update("DELETE FROM post_acl WHERE post_id=? AND rule_type IN ('ALLOW_LIST','ALLOW_USER')", id);
            setAudience(id, viewer, body);
        }
        if (body.get("expectedVersion") != null
                && ((Number) body.get("expectedVersion")).intValue() != ((Number) previous.get("currentVersion"))
                        .intValue()) {
            throw AppException.conflict("This post changed. Reload before editing.");
        }

        // Votes reference block/option indexes; changing those blocks would reinterpret
        // existing votes.
        if (exists("SELECT 1 FROM poll_votes WHERE post_id=?", id)) {
            List<?> previousBlocks = (List<?>) previous.get("blocks");
            for (int index = 0; index < previousBlocks.size(); index++) {
                if (previousBlocks.get(index) instanceof Map<?, ?> block && "poll".equals(block.get("type"))
                        && (index >= blocks.size() || !block.equals(blocks.get(index)))) {
                    throw AppException.conflict("A poll with votes cannot be changed");
                }
            }
        }

        // The snapshot, content, audience and counters commit together or all roll
        // back.
        db.update("""
                INSERT INTO post_versions(post_id,version_number,blocks,text_content,edited_by,edit_summary)
                VALUES(?,?,?::jsonb,?,?,?)
                """, id, previous.get("currentVersion"), encode(previous.get("blocks")), previous.get("textContent"),
                viewer, text(body.get("editSummary"), "Edit summary", 255, false));
        db.update("""
                UPDATE posts SET blocks=?::jsonb,text_content=?,visibility=?,post_type=?,community_id=?,
                    current_version=current_version+1 WHERE id=?
                """, encode(blocks), extractText(blocks), visibility, type, community, id);
        if (!Objects.equals(community, previous.get("communityId"))) {
            if (previous.get("communityId") != null) {
                db.update("UPDATE communities SET post_count=GREATEST(0,post_count-1) WHERE id=?",
                        previous.get("communityId"));
            }
            if (community != null) {
                db.update("UPDATE communities SET post_count=post_count+1 WHERE id=?", community);
            }
        }
        hashtags(id, blocks, body.get("hashtags"));
        return requirePost(id, viewer);
    }

    @Transactional
    public void deletePost(UUID viewer, UUID id) {
        var old = lockAuthor(id, viewer);
        db.update("UPDATE posts SET status='REMOVED' WHERE id=?", id);
        if (!"STORY".equals(old.get("postType"))) {
            db.update(
                    "UPDATE users SET post_count=GREATEST(0,post_count-1) WHERE id IN (SELECT user_id FROM post_authors WHERE post_id=?)",
                    id);
        }
        if (old.get("communityId") != null)
            db.update("UPDATE communities SET post_count=GREATEST(0,post_count-1) WHERE id=?", old.get("communityId"));
    }

    public List<Map<String, Object>> history(UUID viewer, UUID id) {
        requirePost(id, viewer);
        return query("SELECT * FROM post_versions WHERE post_id=? ORDER BY version_number DESC LIMIT 100", id);
    }

    @Transactional
    public Map<String, Object> revert(UUID viewer, UUID id, int version) {
        var v = one("SELECT blocks FROM post_versions WHERE post_id=? AND version_number=?", id, version);
        return updatePost(viewer, id,
                Map.of("blocks", v.get("blocks"), "editSummary", "Reverted to version " + version));
    }

    @Transactional
    public Map<String, Object> engage(UUID viewer, UUID post, String kind, boolean enabled) {
        if (!List.of("like", "save", "view").contains(kind))
            throw AppException.badRequest("Unsupported engagement");
        one("SELECT id FROM posts WHERE id=? FOR UPDATE", post);
        requirePost(post, viewer);
        if (kind.equals("view") && !enabled)
            throw AppException.badRequest("Views cannot be removed");
        int changed = enabled
                ? db.update("INSERT INTO post_engagements(post_id,user_id,kind) VALUES(?,?,?) ON CONFLICT DO NOTHING",
                        post, viewer, kind)
                : db.update("DELETE FROM post_engagements WHERE post_id=? AND user_id=? AND kind=?", post, viewer,
                        kind);
        if (changed > 0) {
            db.update("UPDATE posts SET " + kind + "_count=GREATEST(0," + kind + "_count+?) WHERE id=?",
                    enabled ? 1 : -1, post);
            if (kind.equals("like") && enabled)
                notifyAuthors(post, viewer, "LIKE");
        }
        return requirePost(post, viewer);
    }

    public List<Map<String, Object>> comments(UUID viewer, UUID post, int page, int limit) {
        requirePost(post, viewer);
        var items = query(
                "SELECT c.id,c.post_id,c.author_id,c.parent_id,c.body AS content,c.like_count,c.created_at FROM comments c WHERE c.post_id=? AND c.status='VISIBLE' AND NOT EXISTS(SELECT 1 FROM blocked_users b WHERE (b.blocker_id=? AND b.blocked_id=c.author_id) OR (b.blocked_id=? AND b.blocker_id=c.author_id)) ORDER BY c.created_at,c.id LIMIT ? OFFSET ?",
                post, viewer, viewer, size(limit), offset(page, limit));
        for (var c : items)
            c.put("author", profile(uuid(c.get("authorId")), viewer));
        return items;
    }

    @Transactional
    public Map<String, Object> comment(UUID viewer, UUID post, Map<String, Object> body) {
        one("SELECT id FROM posts WHERE id=? FOR UPDATE", post);
        var p = requirePost(post, viewer);
        if (!Boolean.TRUE.equals(p.get("commentsEnabled")))
            throw AppException.forbidden("Comments are disabled");
        String content = text(body.get("content"), "Comment", 3000, true);
        UUID parent = body.get("parentId") == null ? null : uuid(body.get("parentId"));
        if (parent != null
                && !exists("SELECT 1 FROM comments WHERE id=? AND post_id=? AND status='VISIBLE'", parent, post))
            throw AppException.badRequest("Reply must belong to this post");
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO comments(id,post_id,author_id,parent_id,body) VALUES(?,?,?,?,?)", id, post, viewer,
                parent, content);
        db.update("UPDATE posts SET comment_count=comment_count+1 WHERE id=?", post);
        notifyAuthors(post, viewer, "COMMENT");
        var result = one("SELECT id,post_id,author_id,parent_id,body AS content,created_at FROM comments WHERE id=?",
                id);
        result.put("author", profile(viewer, viewer));
        return result;
    }

    @Transactional
    public void deleteComment(UUID viewer, UUID post, UUID id) {
        one("SELECT id FROM posts WHERE id=? FOR UPDATE", post);
        requirePost(post, viewer);
        int count = db.update(
                "UPDATE comments SET status='REMOVED' WHERE id=? AND post_id=? AND status='VISIBLE' AND (author_id=? OR EXISTS(SELECT 1 FROM post_authors WHERE post_id=? AND user_id=?))",
                id, post, viewer, post, viewer);
        if (count > 0)
            db.update("UPDATE posts SET comment_count=GREATEST(0,comment_count-1) WHERE id=?", post);
    }

    @Transactional
    public Map<String, Object> vote(UUID viewer, UUID post, int blockIndex, int optionIndex) {
        one("SELECT id FROM posts WHERE id=? FOR UPDATE", post);
        var p = requirePost(post, viewer);
        List<?> blocks = (List<?>) p.get("blocks");
        if (blockIndex < 0 || blockIndex >= blocks.size() || !(blocks.get(blockIndex) instanceof Map<?, ?> b)
                || !"poll".equals(b.get("type")))
            throw AppException.badRequest("Poll not found");
        Map<?, ?> data = (Map<?, ?>) b.get("data");
        List<?> options = (List<?>) data.get("options");
        if (optionIndex < 0 || optionIndex >= options.size())
            throw AppException.badRequest("Invalid poll option");
        if (data.get("expiresAt") != null && Instant.parse(data.get("expiresAt").toString()).isBefore(Instant.now()))
            throw AppException.conflict("This poll has ended");
        db.update(
                "INSERT INTO poll_votes(post_id,user_id,block_index,option_index) VALUES(?,?,?,?) ON CONFLICT(post_id,user_id,block_index) DO UPDATE SET option_index=EXCLUDED.option_index",
                post, viewer, blockIndex, optionIndex);
        return requirePost(post, viewer);
    }

    public List<Map<String, Object>> communities(UUID viewer, boolean mine, String search) {
        return query(
                "SELECT c.*,active_community_member(c.id,?) AS is_member FROM communities c WHERE (c.expires_at IS NULL OR c.expires_at>NOW()) AND (c.visibility<>'HIDDEN' OR active_community_member(c.id,?)) AND (?=false OR EXISTS(SELECT 1 FROM community_members cm WHERE cm.community_id=c.id AND cm.user_id=?)) AND (c.name ILIKE ? OR c.description ILIKE ?) ORDER BY c.member_count DESC,c.id LIMIT 50",
                viewer, viewer, mine, viewer, "%" + text(search, "Search", 100, false) + "%",
                "%" + text(search, "Search", 100, false) + "%");
    }

    public Map<String, Object> community(UUID viewer, String slug) {
        return one(
                "SELECT c.*,active_community_member(c.id,?) AS is_member FROM communities c WHERE (c.slug=? OR c.id::text=?) AND (c.expires_at IS NULL OR c.expires_at>NOW()) AND (c.visibility<>'HIDDEN' OR active_community_member(c.id,?))",
                viewer, slug, slug, viewer);
    }

    @Transactional
    public Map<String, Object> createCommunity(UUID viewer, Map<String, Object> body) {
        String name = text(body.get("name"), "Name", 100, true),
                slug = text(body.get("slug"), "Slug", 100, true).toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw AppException.badRequest("Slug must use lowercase letters, numbers, and hyphens");
        String type = choice(body.get("type"), "PERMANENT", "PERMANENT", "TEMPORAL"),
                visibility = choice(body.get("visibility"), "PUBLIC", "PUBLIC", "PRIVATE", "HIDDEN");
        Instant expires = body.get("expiresAt") == null ? null : Instant.parse(body.get("expiresAt").toString());
        if (type.equals("TEMPORAL") && (expires == null || !expires.isAfter(Instant.now())))
            throw AppException.badRequest("Temporal communities need a future expiration");
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO communities(id,owner_id,name,slug,description,community_type,visibility,expires_at,is_paid,member_count) VALUES(?,?,?,?,?,?,?,?,?,1)",
                id, viewer, name, slug, text(body.get("description"), "Description", 5000, false), type, visibility,
                expires == null ? null : Timestamp.from(expires), Boolean.TRUE.equals(body.get("isPaid")));
        db.update("INSERT INTO community_members(community_id,user_id,role) VALUES(?,?,'OWNER')", id, viewer);
        return community(viewer, slug);
    }

    @Transactional
    public void join(UUID viewer, UUID id, boolean enabled) {
        var c = one("SELECT * FROM communities WHERE id=? AND (expires_at IS NULL OR expires_at>NOW()) FOR UPDATE", id);
        if (enabled) {
            if (exists("SELECT 1 FROM community_members WHERE community_id=? AND user_id=?", id, viewer))
                return;
            if (!"PUBLIC".equals(c.get("visibility")))
                throw AppException.forbidden("An invitation is required for this community");
            if (Boolean.TRUE.equals(c.get("isPaid")))
                throw AppException.forbidden("Purchase a membership to join this community");
            int n = db.update("INSERT INTO community_members(community_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",
                    id, viewer);
            if (n > 0)
                db.update("UPDATE communities SET member_count=member_count+1 WHERE id=?", id);
        } else {
            if (viewer.equals(c.get("ownerId")))
                throw AppException.badRequest("The owner cannot leave their community");
            int n = db.update("DELETE FROM community_members WHERE community_id=? AND user_id=?", id, viewer);
            if (n > 0)
                db.update("UPDATE communities SET member_count=GREATEST(0,member_count-1) WHERE id=?", id);
        }
    }

    @Transactional
    public void addMember(UUID owner, UUID community, UUID target) {
        var c = one(
                "SELECT * FROM communities WHERE id=? AND owner_id=? AND (expires_at IS NULL OR expires_at>NOW()) FOR UPDATE",
                community, owner);
        if (Boolean.TRUE.equals(c.get("isPaid")))
            throw AppException.badRequest("Paid memberships must be purchased");
        profile(target, owner);
        if (db.update("INSERT INTO community_members(community_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",
                community, target) > 0)
            db.update("UPDATE communities SET member_count=member_count+1 WHERE id=?", community);
    }

    public Map<String, Object> search(UUID viewer, String q) {
        return Map.of("users", users(viewer, q, 20), "posts", posts(viewer, "discover", 0, 20, null, null, q, null),
                "communities", communities(viewer, false, q));
    }

    private static final String AUDIENCE_LIST_SELECT = """
            SELECT l.*,
            COALESCE((SELECT jsonb_agg(m.user_id ORDER BY m.user_id) FROM acl_list_members m WHERE m.list_id=l.id),'[]'::jsonb) AS user_ids,
            COALESCE((SELECT jsonb_agg(jsonb_build_object('id',u.id,'username',u.username,'displayName',u.display_name,'avatarUrl',u.avatar_url) ORDER BY u.username)
              FROM acl_list_members m JOIN users u ON u.id=m.user_id WHERE m.list_id=l.id AND u.status='ACTIVE'
              AND NOT EXISTS(SELECT 1 FROM blocked_users b WHERE (b.blocker_id=l.owner_id AND b.blocked_id=u.id) OR (b.blocker_id=u.id AND b.blocked_id=l.owner_id))),'[]'::jsonb) AS members
            FROM acl_lists l
            """;

    public List<Map<String, Object>> lists(UUID viewer) {
        return query(AUDIENCE_LIST_SELECT + " WHERE l.owner_id=? ORDER BY l.created_at", viewer);
    }

    @Transactional
    public Map<String, Object> createList(UUID viewer, Map<String, Object> body) {
        one("SELECT id FROM users WHERE id=? FOR UPDATE", viewer);
        if (db.queryForObject("SELECT count(*) FROM acl_lists WHERE owner_id=?", Long.class, viewer) >= 100)
            throw AppException.badRequest("Maximum 100 audience lists");
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO acl_lists(id,owner_id,name) VALUES(?,?,?)", id, viewer,
                text(body.get("name"), "List name", 100, true));
        replaceListMembers(viewer, id, body.getOrDefault("userIds", List.of()));
        return audienceList(viewer, id);
    }

    @Transactional
    public Map<String, Object> updateList(UUID viewer, UUID id, Map<String, Object> body) {
        one("SELECT id FROM acl_lists WHERE id=? AND owner_id=? FOR UPDATE", id, viewer);
        if (body.containsKey("name"))
            db.update("UPDATE acl_lists SET name=? WHERE id=?", text(body.get("name"), "List name", 100, true), id);
        if (body.containsKey("userIds"))
            replaceListMembers(viewer, id, body.get("userIds"));
        return audienceList(viewer, id);
    }

    @Transactional
    public void deleteList(UUID viewer, UUID id) {
        one("SELECT id FROM acl_lists WHERE id=? AND owner_id=? FOR UPDATE", id, viewer);
        db.update("DELETE FROM acl_lists WHERE id=?", id);
    }

    private Map<String, Object> audienceList(UUID viewer, UUID id) {
        return one(AUDIENCE_LIST_SELECT + " WHERE l.id=? AND l.owner_id=?", id, viewer);
    }

    private void replaceListMembers(UUID viewer, UUID id, Object members) {
        if (!(members instanceof List<?> values) || values.size() > 100)
            throw AppException.badRequest("An audience list supports up to 100 people");
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object target : values) {
            UUID user = uuid(target);
            profile(user, viewer);
            ids.add(user);
        }
        db.update("DELETE FROM acl_list_members WHERE list_id=?", id);
        for (UUID user : ids)
            db.update("INSERT INTO acl_list_members(list_id,user_id) VALUES(?,?)", id, user);
    }

    private void notify(UUID user, UUID actor, UUID post, String type) {
        if (!user.equals(actor))
            db.update("INSERT INTO notifications(user_id,actor_id,post_id,type) VALUES(?,?,?,?)", user, actor, post,
                    type);
    }

    private void notifyAuthors(UUID post, UUID actor, String type) {
        db.update(
                "INSERT INTO notifications(user_id,actor_id,post_id,type) SELECT user_id,?,?,? FROM post_authors WHERE post_id=? AND user_id<>?",
                actor, post, type, post, actor);
    }

    public List<Map<String, Object>> notifications(UUID viewer) {
        var items = query(
                "SELECT n.id,n.type,n.post_id,n.actor_id,n.is_read AS read,n.created_at FROM notifications n WHERE n.user_id=? AND (n.post_id IS NULL OR check_post_access(n.post_id,?)) AND NOT EXISTS(SELECT 1 FROM blocked_users b WHERE (b.blocker_id=? AND b.blocked_id=n.actor_id) OR (b.blocked_id=? AND b.blocker_id=n.actor_id)) ORDER BY n.created_at DESC LIMIT 100",
                viewer, viewer, viewer, viewer);
        for (var n : items)
            n.put("actor", profile(uuid(n.get("actorId")), viewer));
        return items;
    }

    @Transactional
    public void readNotifications(UUID viewer) {
        db.update("UPDATE notifications SET is_read=true WHERE user_id=? AND NOT is_read", viewer);
    }

    public List<Map<String, Object>> conversations(UUID viewer) {
        var items = query(
                "SELECT DISTINCT ON (other_id) CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS other_id,content,created_at FROM direct_messages WHERE (sender_id=? OR recipient_id=?) AND deleted_at IS NULL AND (expires_at IS NULL OR expires_at>NOW()) ORDER BY other_id,created_at DESC",
                viewer, viewer, viewer);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var m : items) {
            UUID other = uuid(m.get("otherId"));
            try {
                var row = new LinkedHashMap<String, Object>();
                row.put("id", other);
                row.put("user", profile(other, viewer));
                row.put("lastMessage", Map.of("content", m.get("content"), "createdAt", m.get("createdAt")));
                row.put("unreadCount", db.queryForObject(
                        "SELECT count(*) FROM direct_messages WHERE sender_id=? AND recipient_id=? AND read_at IS NULL AND deleted_at IS NULL AND (expires_at IS NULL OR expires_at>NOW())",
                        Long.class, other, viewer));
                out.add(row);
            } catch (AppException ignored) {
            }
        }
        out.sort((a, b) -> ((Instant) ((Map<?, ?>) b.get("lastMessage")).get("createdAt"))
                .compareTo((Instant) ((Map<?, ?>) a.get("lastMessage")).get("createdAt")));
        return out;
    }

    @Transactional
    public List<Map<String, Object>> messages(UUID viewer, UUID other, int page, int limit) {
        profile(other, viewer);
        db.update("UPDATE direct_messages SET read_at=NOW() WHERE sender_id=? AND recipient_id=? AND read_at IS NULL",
                other, viewer);
        var items = query(
                "SELECT * FROM direct_messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) AND deleted_at IS NULL AND (expires_at IS NULL OR expires_at>NOW()) ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                viewer, other, other, viewer, size(limit), offset(page, limit));
        Collections.reverse(items);
        return items;
    }

    @Transactional
    public Map<String, Object> sendMessage(UUID viewer, UUID other, Map<String, Object> body) {
        if (viewer.equals(other))
            throw AppException.badRequest("Choose another person");
        profile(other, viewer);
        String content = text(body.get("content"), "Message", 10000, true);
        UUID id = UUID.randomUUID(), client = body.get("clientId") == null ? null : uuid(body.get("clientId"));
        Integer ttl = body.get("ttlSeconds") instanceof Number n ? n.intValue() : null;
        if (ttl != null && (ttl < 10 || ttl > 604800))
            throw AppException.badRequest("Message expiry must be between 10 seconds and 7 days");
        db.update(
                "INSERT INTO direct_messages(id,sender_id,recipient_id,content,client_id,expires_at) VALUES(?,?,?,?,?,?) ON CONFLICT(sender_id,client_id) DO NOTHING",
                id, viewer, other, content, client,
                ttl == null ? null : Timestamp.from(Instant.now().plusSeconds(ttl)));
        var result = client == null ? one("SELECT * FROM direct_messages WHERE id=?", id)
                : one("SELECT * FROM direct_messages WHERE sender_id=? AND client_id=?", viewer, client);
        if (!other.equals(result.get("recipientId")) || !content.equals(result.get("content")))
            throw AppException.conflict("This client ID was already used for a different message");
        return result;
    }

    @Transactional
    public void deleteMessage(UUID viewer, UUID id) {
        if (db.update("UPDATE direct_messages SET deleted_at=COALESCE(deleted_at,NOW()) WHERE id=? AND sender_id=?", id,
                viewer) == 0)
            throw AppException.forbidden("Only the sender can delete a message");
    }

    @Transactional
    public Map<String, Object> restoreMessage(UUID viewer, UUID id) {
        if (db.update(
                "UPDATE direct_messages SET deleted_at=NULL WHERE id=? AND sender_id=? AND deleted_at>NOW()-INTERVAL '20 seconds' AND (expires_at IS NULL OR expires_at>NOW())",
                id, viewer) == 0)
            throw AppException.conflict("The 20-second undo window has ended");
        return one("SELECT * FROM direct_messages WHERE id=?", id);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10000)
    @Transactional
    public void purgeMessages() {
        db.update("DELETE FROM direct_messages WHERE deleted_at<=NOW()-INTERVAL '20 seconds' OR expires_at<=NOW()");
    }
}
