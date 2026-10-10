package com.dbgram.service;

import com.dbgram.util.AppException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class CommunityEventService {
 private final JdbcTemplate db;
 public CommunityEventService(JdbcTemplate db) { this.db=db; }
 public record Event(UUID id, UUID communityId, UUID creatorId, String title, String description, Instant startsAt, Instant endsAt, String meetLink, Integer maxAttendees, int rsvpCount, String status, boolean attending) {}
 public record Draft(String title,String description,Instant startsAt,Instant endsAt,String meetLink,Integer maxAttendees) {}
    private void member(UUID actor, UUID community) {
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT active_community_member(?,?)", Boolean.class, community, actor))) {
            throw AppException.forbidden("An active community membership is required");
        }
    }

    private void manager(UUID actor, UUID community) {
        member(actor, community);
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM community_members WHERE community_id=? AND user_id=? AND role IN ('OWNER','ADMIN','MODERATOR'))", Boolean.class, community, actor))) {
            throw AppException.forbidden("Only community organizers can manage events");
        }
    }
 private Event map(ResultSet r) throws SQLException {
  Instant start=r.getTimestamp("starts_at").toInstant(),end=r.getTimestamp("ends_at").toInstant();
  String status=r.getString("status");
  if(!"CANCELED".equals(status)) status=Instant.now().isAfter(end)?"ENDED":Instant.now().isAfter(start)?"LIVE":"UPCOMING";
  return new Event(r.getObject("id",UUID.class),r.getObject("community_id",UUID.class),r.getObject("creator_id",UUID.class),r.getString("title"),r.getString("description"),start,end,r.getString("meet_link"),(Integer)r.getObject("max_attendees"),r.getInt("rsvp_count"),status,r.getBoolean("attending"));
 }
 @Transactional(readOnly=true)
 public List<Event> list(UUID actor,UUID community) {
  member(actor,community);
  return db.query("SELECT e.*,EXISTS(SELECT 1 FROM event_rsvps r WHERE r.event_id=e.id AND r.user_id=?) attending FROM community_events e WHERE community_id=? ORDER BY starts_at DESC LIMIT 100",(r,n)->map(r),actor,community);
 }
    @Transactional
    public Event create(UUID actor, UUID community, Draft d) {
        manager(actor, community);
        if (!d.endsAt().isAfter(d.startsAt()) || !d.endsAt().isAfter(Instant.now())) {
            throw AppException.badRequest("Choose a future event with an end after its start");
        }
        if (d.meetLink() != null && !d.meetLink().isBlank() && !d.meetLink().matches("https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}")) {
            throw AppException.badRequest("Use a valid Google Meet join link");
        }
        List<Timestamp> expires = db.query("SELECT expires_at FROM communities WHERE id=?", (r, n) -> r.getTimestamp(1), community);
        if (!expires.isEmpty() && expires.getFirst() != null && d.endsAt().isAfter(expires.getFirst().toInstant())) {
            throw AppException.badRequest("The event must end before the community expires");
        }
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO community_events(id,community_id,creator_id,title,description,starts_at,ends_at,meet_link,max_attendees) VALUES (?,?,?,?,?,?,?,?,?)", id, community, actor, d.title().trim(), d.description(), Timestamp.from(d.startsAt()), Timestamp.from(d.endsAt()), d.meetLink(), d.maxAttendees());
        return get(actor, id, false);
    }

    private Event get(UUID actor, UUID id, boolean lock) {
        var events = db.query("SELECT e.*,EXISTS(SELECT 1 FROM event_rsvps r WHERE r.event_id=e.id AND r.user_id=?) attending FROM community_events e WHERE e.id=?" + (lock ? " FOR UPDATE OF e" : ""), (r, n) -> map(r), actor, id);
        if (events.isEmpty()) throw AppException.notFound("Event", id);
        Event event = events.getFirst();
        member(actor, event.communityId());
        return event;
    }
    @Transactional
    public Event rsvp(UUID actor, UUID id, boolean attending) {
        Event event = get(actor, id, true);
        if (attending) {
            if (Set.of("CANCELED", "ENDED").contains(event.status())) {
                throw AppException.badRequest("This event is no longer accepting RSVPs");
            }
            if (!event.attending() && event.maxAttendees() != null && event.rsvpCount() >= event.maxAttendees()) {
                throw AppException.conflict("This event is full");
            }
            int change = db.update("INSERT INTO event_rsvps(event_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", id, actor);
            db.update("UPDATE community_events SET rsvp_count=rsvp_count+? WHERE id=?", change, id);
        } else {
            int change = db.update("DELETE FROM event_rsvps WHERE event_id=? AND user_id=?", id, actor);
            db.update("UPDATE community_events SET rsvp_count=GREATEST(0,rsvp_count-?) WHERE id=?", change, id);
        }
        return get(actor, id, false);
    }

    @Transactional
    public Event cancel(UUID actor, UUID id) {
        Event event = get(actor, id, true);
        manager(actor, event.communityId());
        db.update("UPDATE community_events SET status='CANCELED' WHERE id=?", id);
        return get(actor, id, false);
    }
}
