package com.dbgram.service;
import com.dbgram.util.AppException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PostContentValidationTest {
 private Map<String,Object> block(String type,Map<String,Object> data) {return Map.of("type",type,"data",data);}
 @Test void supportsStructuredBlocksWithoutExecutingMarkup() {
  var content=List.of(block("text",Map.of("content","Hello #builders")),block("code",Map.of("content","console.log('hello')","language","js")),block("carousel",Map.of("media_urls",List.of("https://example.com/photo.png"))),block("poll",Map.of("question","Ship it?","options",List.of("Yes","No"))),block("embed",Map.of("url","https://example.com")));
  assertEquals(5,SocialService.validateBlocks(content).size());
 }
 @Test void rejectsExecutableUrlsAndMissingMedia() {
  assertThrows(AppException.class,()->SocialService.validateBlocks(List.of(block("embed",Map.of("url","javascript:alert(1)")))));
  assertThrows(AppException.class,()->SocialService.validateBlocks(List.of(block("image",Map.of()))));
  assertThrows(AppException.class,()->SocialService.safeUrl("data:text/html,<script>alert(1)</script>"));
 }
 @Test void rejectsPollsWithoutARealChoiceAndInvalidShapes() {
  assertThrows(AppException.class,()->SocialService.validateBlocks(List.of(block("poll",Map.of("question","Q","options",List.of("Only"))))));
  assertThrows(AppException.class,()->SocialService.validateBlocks(List.of(Map.of("type","text","data","bad"))));
  assertThrows(AppException.class,()->SocialService.validateBlocks(List.of(block("text",Map.of("content",Map.of("untrusted","shape"))))));
  assertThrows(AppException.class,()->SocialService.validateBlocks(Collections.nCopies(31,block("text",Map.of("content","x")))));
 }
 @Test void boundsPaginationToPreventUnboundedQueries() {assertEquals(50,SocialService.size(50));assertThrows(AppException.class,()->SocialService.size(100000));assertThrows(AppException.class,()->SocialService.size(0));}
 @Test void mediaLinksRejectLookalikeLocalHostsAndCredentials() {
  for(String url:List.of("http://localhost.evil.test/image.png","https://user:password@host.test/image.png","javascript:alert(1)","//host.test/image.png"))
   assertThrows(AppException.class,()->SocialService.safeUrl(url));
  assertEquals("https://media.example.com/image.png",SocialService.safeUrl("https://media.example.com/image.png"));
 }
}
