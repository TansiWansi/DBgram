package com.dbgram.controller;

import com.dbgram.domain.post.Post;
import com.dbgram.domain.post.PostVersion;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.PostService;
import com.dbgram.util.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Post REST controller — CRUD, versioning, edit history, and revert.
 */
@org.springframework.context.annotation.Profile("legacy-api")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

	private final PostService postService;

	@PostMapping
	public ResponseEntity<ApiResponse<Post>> createPost(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody CreatePostRequest request) {
		Post post = postService.createPost(
				principal.id(), request.blocks(), request.postType(),
				request.visibility(), request.hashtags(), request.communityId());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok("Post created", post));
	}

	@GetMapping("/{postId}")
	public ResponseEntity<ApiResponse<Post>> getPost(@PathVariable UUID postId) {
		return ResponseEntity.ok(ApiResponse.ok(postService.getPostById(postId)));
	}

	@PutMapping("/{postId}")
	public ResponseEntity<ApiResponse<Post>> updatePost(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID postId,
			@Valid @RequestBody UpdatePostRequest request) {
		Post post = postService.updatePost(
				postId, principal.id(), request.blocks(), request.editSummary());
		return ResponseEntity.ok(ApiResponse.ok("Post updated", post));
	}

	@DeleteMapping("/{postId}")
	public ResponseEntity<ApiResponse<Void>> deletePost(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID postId) {
		postService.deletePost(postId, principal.id());
		return ResponseEntity.ok(ApiResponse.ok("Post deleted", null));
	}

	@GetMapping("/user/{userId}")
	public ResponseEntity<ApiResponse<Page<Post>>> getUserPosts(
			@PathVariable UUID userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(ApiResponse.ok(
				postService.getPostsByAuthor(userId,
						PageRequest.of(page, size,
								Sort.by(Sort.Direction.DESC, "publishedAt")))));
	}

	@GetMapping("/{postId}/history")
	public ResponseEntity<ApiResponse<List<PostVersion>>> getEditHistory(
			@PathVariable UUID postId) {
		return ResponseEntity.ok(ApiResponse.ok(postService.getEditHistory(postId)));
	}

	@PostMapping("/{postId}/revert/{version}")
	public ResponseEntity<ApiResponse<Post>> revertToVersion(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID postId,
			@PathVariable int version) {
		Post post = postService.revertToVersion(postId, principal.id(), version);
		return ResponseEntity.ok(ApiResponse.ok("Post reverted to version " + version, post));
	}

	// ── Request DTOs ──

	public record CreatePostRequest(
			@NotEmpty List<Map<String, Object>> blocks,
			Post.PostType postType,
			Post.PostVisibility visibility,
			String[] hashtags,
			UUID communityId) {
	}

	public record UpdatePostRequest(
			@NotEmpty List<Map<String, Object>> blocks,
			String editSummary) {
	}
}
