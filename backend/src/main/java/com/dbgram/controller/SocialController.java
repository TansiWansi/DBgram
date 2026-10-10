package com.dbgram.controller;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.SocialService;
import com.dbgram.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SocialController {
    private final SocialService social;
    private final com.dbgram.service.CursorFeedService feeds;

    @GetMapping("/users/me")
    public ApiResponse<?> me(@AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.ok(social.profile(u.id(), u.id()));
    }

    @GetMapping("/users/suggested")
    public ApiResponse<?> suggested(@AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.ok(social.users(u.id(), "", 10));
    }

    @GetMapping("/users/{username}")
    public ApiResponse<?> user(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String username) {
        return ApiResponse.ok(social.profile(username, u.id()));
    }

    @PutMapping("/users/me")
    public ApiResponse<?> updateUser(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(social.updateProfile(u.id(), body));
    }

    @PutMapping("/users/me/privacy")
    public ApiResponse<?> privacy(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody AccountPrivacy body) {
        return ApiResponse.ok(social.accountPrivacy(u.id(), body.isPrivate()));
    }

    @GetMapping("/users/me/follow-requests")
    public ApiResponse<?> followRequests(@AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.ok(social.followRequests(u.id()));
    }

    @PostMapping("/users/me/follow-requests/{id}/accept")
    public ApiResponse<?> acceptFollow(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        social.respondFollowRequest(u.id(), id, true);
        return ApiResponse.ok("Follow request accepted", null);
    }

    @DeleteMapping("/users/me/follow-requests/{id}")
    public ApiResponse<?> declineFollow(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        social.respondFollowRequest(u.id(), id, false);
        return ApiResponse.ok("Follow request declined", null);
    }

    @DeleteMapping("/users/me/followers/{id}")
    public ApiResponse<?> removeFollower(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        social.follow(id, u.id(), false);
        return ApiResponse.ok("Follower removed", null);
    }

    public record AccountPrivacy(Boolean isPrivate) {}

    @PostMapping("/users/{id}/follow")
    public ApiResponse<?> follow(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.follow(u.id(), id, true));
    }

    @DeleteMapping("/users/{id}/follow")
    public ApiResponse<?> unfollow(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.follow(u.id(), id, false));
    }

    @GetMapping("/users/{id}/followers")
    public ApiResponse<?> followers(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.connections(id, u.id(), true));
    }

    @GetMapping("/users/{id}/following")
    public ApiResponse<?> following(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.connections(id, u.id(), false));
    }

    @GetMapping("/users/{id}/is-following")
    public ApiResponse<?> isFollowing(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(Map.of("isFollowing", social.profile(id, u.id()).get("isFollowing")));
    }

    @PutMapping("/users/{id}/close-friend")
    public ApiResponse<?> closeFriend(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody Toggle body) {
        social.closeFriend(u.id(), id, body.enabled());
        return ApiResponse.ok("Close friends updated", null);
    }

    @PostMapping("/users/{id}/block")
    public ApiResponse<?> block(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        social.block(u.id(), id, true);
        return ApiResponse.ok("Blocked", null);
    }

    @DeleteMapping("/users/{id}/block")
    public ApiResponse<?> unblock(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        social.block(u.id(), id, false);
        return ApiResponse.ok("Unblocked", null);
    }

    @GetMapping("/feed")
    public ApiResponse<?> feed(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(defaultValue = "discover") String mode, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String postType, @RequestParam(required = false) UUID communityId) {
        return ApiResponse.ok(social.posts(u.id(), mode, page, size, null, communityId, null, postType));
    }

    @GetMapping("/feed/page")
    public ApiResponse<?> feedPage(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(defaultValue = "discover") String mode, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String postType, @RequestParam(required = false) UUID communityId) {
        return ApiResponse.ok(feeds.page(u.id(), mode, cursor, size, postType, communityId));
    }

    @GetMapping("/reels")
    public ApiResponse<?> reels(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(social.posts(u.id(), "discover", page, size, null, null, null, "REEL"));
    }

    @GetMapping("/posts/saved")
    public ApiResponse<?> saved(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(social.posts(u.id(), "saved", page, size, null, null, null, null));
    }

    @GetMapping("/posts/user/{id}")
    public ApiResponse<?> userPosts(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(social.posts(u.id(), "discover", page, size, id, null, null, null));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<?> post(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.requirePost(id, u.id()));
    }

    @PostMapping("/posts")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<?> create(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(social.createPost(u.id(), body));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<?> update(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(social.updatePost(u.id(), id, body));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<?> delete(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        social.deletePost(u.id(), id);
        return ApiResponse.ok("Post deleted", null);
    }

    @GetMapping("/posts/{id}/history")
    public ApiResponse<?> history(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.history(u.id(), id));
    }

    @PostMapping("/posts/{id}/revert/{version}")
    public ApiResponse<?> revert(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @PathVariable int version) {
        return ApiResponse.ok(social.revert(u.id(), id, version));
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<?> like(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.engage(u.id(), id, "like", true));
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<?> unlike(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.engage(u.id(), id, "like", false));
    }

    @PostMapping("/posts/{id}/save")
    public ApiResponse<?> save(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.engage(u.id(), id, "save", true));
    }

    @DeleteMapping("/posts/{id}/save")
    public ApiResponse<?> unsave(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.engage(u.id(), id, "save", false));
    }

    @PostMapping("/posts/{id}/view")
    public ApiResponse<?> view(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id) {
        return ApiResponse.ok(social.engage(u.id(), id, "view", true));
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<?> comments(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(social.comments(u.id(), id, page, size));
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<?> comment(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(social.comment(u.id(), id, body));
    }

    @DeleteMapping("/posts/{id}/comments/{comment}")
    public ApiResponse<?> deleteComment(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @PathVariable UUID comment) {
        social.deleteComment(u.id(), id, comment);
        return ApiResponse.ok("Comment deleted", null);
    }

    @PostMapping("/posts/{id}/poll/vote")
    public ApiResponse<?> vote(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody Vote body) {
        return ApiResponse.ok(social.vote(u.id(), id, body.blockIndex(), body.optionIndex()));
    }

    @PostMapping("/posts/{id}/authors/invite")
    public ApiResponse<?> inviteAuthor(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody AuthorInvite body) {
        return ApiResponse.ok(social.inviteAuthor(u.id(), id, body.userId(), body.contributionPct()));
    }

    @PutMapping("/posts/{id}/authors/contributions")
    public ApiResponse<?> contributions(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody Map<String, Integer> body) {
        return ApiResponse.ok(social.contributions(u.id(), id, body));
    }

    @GetMapping("/collaborations")
    public ApiResponse<?> invitations(@AuthenticationPrincipal AuthenticatedUser u) {
        return ApiResponse.ok(social.invitations(u.id()));
    }

    @PostMapping("/collaborations/{id}")
    public ApiResponse<?> respond(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable UUID id, @RequestBody Accept body) {
        return ApiResponse.ok(social.respondInvitation(u.id(), id, body.accept()));
    }

 @GetMapping("/communities") public ApiResponse<?> communities(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(defaultValue="") String q) {return ApiResponse.ok(social.communities(u.id(),false,q));}
 @GetMapping("/communities/me") public ApiResponse<?> myCommunities(@AuthenticationPrincipal AuthenticatedUser u) {return ApiResponse.ok(social.communities(u.id(),true,""));}
 @GetMapping("/communities/{slug}") public ApiResponse<?> community(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String slug) {return ApiResponse.ok(social.community(u.id(),slug));}
 @PostMapping("/communities") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public ApiResponse<?> createCommunity(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> body) {return ApiResponse.ok(social.createCommunity(u.id(),body));}
 @PostMapping("/communities/{id}/join") public ApiResponse<?> join(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id) {social.join(u.id(),id,true);return ApiResponse.ok("Community joined",null);}
 @DeleteMapping({"/communities/{id}/leave","/communities/{id}/join"}) public ApiResponse<?> leave(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id) {social.join(u.id(),id,false);return ApiResponse.ok("Community left",null);}
 @PostMapping("/communities/{id}/members/{target}") public ApiResponse<?> addMember(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id,@PathVariable UUID target) {social.addMember(u.id(),id,target);return ApiResponse.ok("Member added",null);}
 @GetMapping("/communities/{id}/posts") public ApiResponse<?> communityPosts(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.ok(social.posts(u.id(),"discover",page,size,null,id,null,null));}
 @GetMapping("/search") public ApiResponse<?> search(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam String q) {return ApiResponse.ok(social.search(u.id(),q));}
 @GetMapping("/acl/lists") public ApiResponse<?> lists(@AuthenticationPrincipal AuthenticatedUser u) {return ApiResponse.ok(social.lists(u.id()));}
 @PostMapping("/acl/lists") public ApiResponse<?> createList(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> body) {return ApiResponse.ok(social.createList(u.id(),body));}
 @PutMapping("/acl/lists/{id}") public ApiResponse<?> updateList(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id,@RequestBody Map<String,Object> body) {return ApiResponse.ok(social.updateList(u.id(),id,body));}
 @DeleteMapping("/acl/lists/{id}") public ApiResponse<?> deleteList(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id) {social.deleteList(u.id(),id);return ApiResponse.ok("Audience list removed",null);}
 @GetMapping("/notifications") public ApiResponse<?> notifications(@AuthenticationPrincipal AuthenticatedUser u) {return ApiResponse.ok(social.notifications(u.id()));}
 @PatchMapping("/notifications/read") public ApiResponse<?> read(@AuthenticationPrincipal AuthenticatedUser u) {social.readNotifications(u.id());return ApiResponse.ok("Notifications read",null);}
 @GetMapping("/messages/conversations") public ApiResponse<?> conversations(@AuthenticationPrincipal AuthenticatedUser u) {return ApiResponse.ok(social.conversations(u.id()));}
 @GetMapping("/messages/{id}") public ApiResponse<?> messages(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return ApiResponse.ok(social.messages(u.id(),id,page,size));}
 @PostMapping("/messages/{id}") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public ApiResponse<?> send(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id,@RequestBody Map<String,Object> body) {return ApiResponse.ok(social.sendMessage(u.id(),id,body));}
 @DeleteMapping("/messages/{id}") public ApiResponse<?> undo(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id) {social.deleteMessage(u.id(),id);return ApiResponse.ok("Message deleted",null);}
 @PostMapping("/messages/{id}/restore") public ApiResponse<?> restore(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable UUID id) {return ApiResponse.ok(social.restoreMessage(u.id(),id));}
 public record Vote(int blockIndex,int optionIndex) {}
 public record Toggle(boolean enabled) {}
 public record AuthorInvite(UUID userId,int contributionPct) {}
 public record Accept(boolean accept) {}
}


