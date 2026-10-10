package com.dbgram.service;

import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class CopyrightClaimService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Set<String> moderators;
    public CopyrightClaimService(JdbcTemplate jdbc, ObjectMapper json, @Value("${moderation.admin-user-ids:}") String admins) {
        this.jdbc = jdbc;
        this.json = json;
        this.moderators = new HashSet<>(Arrays.asList(admins.split(",")));
    }

    public boolean isModerator(UUID user) {
        return moderators.contains(user.toString());
    }
    @Transactional
    public Map<String, Object> submit(UUID user, UUID post, UUID media, String type, String description, List<String> evidence, String original, String hash) {
        if (post == null && media == null) throw AppException.badRequest("Choose a post or uploaded media");
        if (description == null || description.strip().length() < 20 || description.length() > 10000) throw AppException.badRequest("Describe the issue in 20 to 10000 characters");
        if (!Set.of("COPYRIGHT", "TRADEMARK", "IDENTITY", "OTHER").contains(type)) throw AppException.badRequest("Invalid claim type");
        List<String> urls = evidence == null ? List.of() : evidence;
        if (urls.size() > 10) throw AppException.badRequest("Maximum 10 evidence links");
        urls.forEach(this::validateUrl);
        if (original != null && !original.isBlank()) validateUrl(original);
        if (hash != null && !hash.matches("[0-9a-fA-F]{64}")) throw AppException.badRequest("SHA-256 hash must contain 64 hexadecimal characters");
        if (post != null && !Boolean.TRUE.equals(jdbc.queryForObject("select check_post_access(?,?)", Boolean.class, post, user))) throw AppException.notFound("Post", post);
        UUID targetUser = null;
        String targetHash = null;
        if (media != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("select uploader_id,sha256_hash from media_assets where id=?", media);
            if (rows.isEmpty()) throw AppException.notFound("Media", media);
            targetUser = (UUID) rows.getFirst().get("uploader_id");
            targetHash = (String) rows.getFirst().get("sha256_hash");
        }
        if (post != null) {
            List<UUID> authors = jdbc.queryForList("select user_id from post_authors where post_id=? order by added_at limit 1", UUID.class, post);
            if (!authors.isEmpty()) targetUser = authors.getFirst();
        }
        Long recent = jdbc.queryForObject("select count(*) from copyright_claims where claimant_id=? and created_at>now()-interval '1 day'", Long.class, user);
        if (recent != null && recent >= 20) throw AppException.badRequest("Daily report limit reached");
        UUID id = UUID.randomUUID();
        String email=jdbc.queryForObject("select email from users where id=?",String.class,user);
        jdbc.update("""
            insert into copyright_claims(id,claimant_id,claimant_email,target_post_id,target_media_id,target_user_id,claim_type,description,
            evidence_urls,original_work_url,claimant_media_hash,target_media_hash,hash_match,status)
            values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,'PENDING')
            """,id,user,email,post,media,targetUser,type,description.strip(),write(urls),original,hash,targetHash,
            hash==null||targetHash==null?null:hash.equalsIgnoreCase(targetHash));
        event(id,user,"PENDING","Report submitted");
        return get(id,user);
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID user) {
        List<Map<String, Object>> rows = isModerator(user) ? jdbc.queryForList("select * from copyright_claims order by created_at desc limit 100") :
                jdbc.queryForList("select * from copyright_claims where claimant_id=? or target_user_id=? order by created_at desc limit 100", user, user);
        return rows.stream().map(row -> response(row, user)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id, UUID user) {
        Map<String, Object> claim = load(id, false);
        authorize(claim, user);
        Map<String, Object> result = response(claim, user);
        result.put("events", jdbc.queryForList("select action,notes,created_at,actor_id from copyright_claim_events where claim_id=? order by created_at", id).stream().map(this::camel).toList());
        return result;
    }
    @Transactional
    public Map<String, Object> transition(UUID id, UUID actor, String status, String notes, Boolean restore) {
        Map<String, Object> claim = load(id, true);
        authorize(claim, actor);
        if (status == null) throw AppException.badRequest("Status is required");
        if (notes == null || notes.strip().length() < 10 || notes.length() > 10000)
            throw AppException.badRequest("Provide 10 to 10000 characters of supporting notes");
        String current = String.valueOf(claim.get("status"));
        boolean mod = isModerator(actor);
        boolean claimant = actor.equals(claim.get("claimant_id"));
        boolean respondent = actor.equals(claim.get("target_user_id"));
        boolean valid = switch (status) {
            case "WITHDRAWN" -> claimant && Set.of("PENDING", "REVIEWING").contains(current);
            case "REVIEWING" -> mod && current.equals("PENDING");
            case "UPHELD", "REJECTED" -> mod && current.equals("REVIEWING");
            case "COUNTER_CLAIMED" -> respondent && current.equals("UPHELD");
            case "COUNTER_REVIEWING" -> mod && current.equals("COUNTER_CLAIMED");
            case "RESOLVED" -> mod && current.equals("COUNTER_REVIEWING");
            default -> false;
        };
        if (!valid) throw AppException.forbidden("This transition is not permitted");
        jdbc.update("update copyright_claims set status=?,resolution_notes=?,resolved_by=?,resolved_at=case when ? then now() else resolved_at end,updated_at=now() where id=?",
                status, notes.strip(), mod ? actor : null, Set.of("UPHELD", "REJECTED", "RESOLVED", "WITHDRAWN").contains(status), id);
        if (status.equals("UPHELD")) {
            if (claim.get("target_post_id") != null)
                jdbc.update("update posts set status='UNDER_REVIEW' where id=?", claim.get("target_post_id"));
            if (claim.get("target_media_id") != null) {
                jdbc.update("update media_uploads set status='QUARANTINED' where id=?", claim.get("target_media_id"));
                jdbc.update("update media_assets set upload_status='FAILED' where id=?", claim.get("target_media_id"));
            }
        }
        if (status.equals("RESOLVED") && Boolean.TRUE.equals(restore)) {
            Long others = jdbc.queryForObject("select count(*) from copyright_claims where id<>? and (target_post_id=? or target_media_id=?) and status in ('UPHELD','COUNTER_CLAIMED','COUNTER_REVIEWING')", Long.class, id, claim.get("target_post_id"), claim.get("target_media_id"));
            if (others != null && others > 0) throw AppException.conflict("Other upheld claims still restrict this content");
            if (claim.get("target_post_id") != null)
                jdbc.update("update posts set status='PUBLISHED' where id=? and status='UNDER_REVIEW'", claim.get("target_post_id"));
            if (claim.get("target_media_id") != null) {
                jdbc.update("update media_uploads set status='READY' where id=? and status='QUARANTINED'", claim.get("target_media_id"));
                jdbc.update("update media_assets set upload_status='READY' where id=?", claim.get("target_media_id"));
            }
        }
        event(id, actor, status, notes.strip());
        return get(id, actor);
    }

    private Map<String, Object> load(UUID id, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from copyright_claims where id=?" + (lock ? " for update" : ""), id);
        if (rows.isEmpty()) throw AppException.notFound("Claim", id);
        return rows.getFirst();
    }

    private void authorize(Map<String, Object> claim, UUID user) {
        if (!isModerator(user) && !user.equals(claim.get("claimant_id")) && !user.equals(claim.get("target_user_id")))
            throw AppException.notFound("Claim", claim.get("id"));
    }

    private void event(UUID claim, UUID actor, String action, String notes) {
        jdbc.update("insert into copyright_claim_events(id,claim_id,actor_id,action,notes) values(?,?,?,?,?)", UUID.randomUUID(), claim, actor, action, notes);
    }
    private Map<String, Object> response(Map<String, Object> row, UUID user) {
        Map<String, Object> visible = new LinkedHashMap<>(row);
        if (!isModerator(user) && !user.equals(row.get("claimant_id"))) visible.remove("claimant_email");
        Object evidence = visible.get("evidence_urls");
        if (evidence != null) {
            try {
                visible.put("evidence_urls", json.readValue(evidence.toString(), List.class));
            } catch (Exception ignored) {
                visible.put("evidence_urls", List.of());
            }
        }
        return camel(visible);
    }

    private Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            StringBuilder name = new StringBuilder();
            boolean up = false;
            for (char c : key.toCharArray()) {
                if (c == '_') up = true;
                else {
                    name.append(up ? Character.toUpperCase(c) : c);
                    up = false;
                }
            }
            out.put(name.toString(), value);
        });
        return out;
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (url.length() > 2048 || !Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw AppException.badRequest("Evidence links must be valid HTTP or HTTPS URLs");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw AppException.badRequest("Invalid evidence");
        }
    }
}
