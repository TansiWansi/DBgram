package com.dbgram.service;
import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CursorFeedServiceTest {
 private final CursorFeedService feeds=new CursorFeedService(mock(JdbcTemplate.class),mock(SocialService.class),new ObjectMapper().registerModule(new JavaTimeModule()));
 @Test void retainsTimestampAndUuidTiebreakerWithoutLosingPrecision() {
  Instant now=Instant.now();UUID id=UUID.randomUUID();var cursor=new CursorFeedService.Cursor("viewer:discover::",now,now.minusNanos(1234),id,null,0);
  assertEquals(cursor,feeds.decode(feeds.encode(cursor),"viewer:discover::"));
 }
 @Test void cursorCannotBeReusedForADifferentUserOrAudience() {
  var cursor=new CursorFeedService.Cursor("alice:following::",Instant.now(),null,null,null,0);
  assertThrows(AppException.class,()->feeds.decode(feeds.encode(cursor),"bob:following::"));
 }
 @Test void rejectsMalformedFutureOrPartialCursor() {
  assertThrows(AppException.class,()->feeds.decode("not-base64!","scope"));
  assertThrows(AppException.class,()->feeds.decode(feeds.encode(new CursorFeedService.Cursor("scope",Instant.now().plusSeconds(3600),null,null,null,0)),"scope"));
  assertThrows(AppException.class,()->feeds.decode(feeds.encode(new CursorFeedService.Cursor("scope",Instant.now(),Instant.now(),null,null,0)),"scope"));
 }
}
