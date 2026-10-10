package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.crypto.MediaProvenanceService;
import com.dbgram.service.MediaUploadService;
import com.dbgram.util.ApiResponse;
import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaUploadService uploads;
    private final MediaProvenanceService provenance;

    @PostMapping("/provenance/verify")
    public ApiResponse<?> verify(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Proof proof) {
        require(user);
        return ApiResponse.ok(Map.of("valid", provenance.verify(proof.leafHash(), proof.merkleRoot(), proof.siblings()),
                "anchored", false));
    }

    @PostMapping("/provenance/batch")
    public ApiResponse<?> batch(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Batch batch) {
        return ApiResponse.ok(uploads.batch(require(user), batch.ids()));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<?> verifyFile(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user)
            throws IOException {
        return ApiResponse.ok(uploads.verifyStored(id, require(user)));
    }

    @PostMapping("/{id}/transcode")
    public ApiResponse<?> retry(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        uploads.retryTranscode(id, require(user));
        return ApiResponse.ok(Map.of("status", "QUEUED"));
    }

    @GetMapping("/mine")
    public ApiResponse<?> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(uploads.mine(require(user)));
    }

    @GetMapping("/{id}/metadata")
    public ApiResponse<?> metadata(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(uploads.metadata(id, require(user)));
    }

    @GetMapping("/{id}/provenance")
    public ApiResponse<?> provenance(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int leaf) throws IOException {
        return ApiResponse.ok(uploads.proof(id, require(user), leaf));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> content(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UUID viewer, @RequestParam(required = false) Long expires,
            @RequestParam(required = false) String signature) throws IOException {
        var record = uploads.content(id, user == null ? null : user.id(), viewer, expires, signature);
        Path filePath = uploads.file(id);
        if (!Files.isRegularFile(filePath)) {
            throw AppException.notFound("Media file", id.toString());
        }
        boolean inline = record.getMimeType().startsWith("image/") || record.getMimeType().startsWith("video/")
                || record.getMimeType().startsWith("audio/");
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(record.getFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(record.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(Files.size(uploads.file(id))).body(new FileSystemResource(uploads.file(id)));
    }

    @GetMapping("/{id}/variants/{name}")
    public ResponseEntity<Resource> variant(@PathVariable UUID id, @PathVariable String name,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UUID viewer, @RequestParam(required = false) Long expires,
            @RequestParam(required = false) String signature) throws IOException {
        uploads.content(id, user == null ? null : user.id(), viewer, expires, signature);
        if (!name.matches("[A-Za-z0-9_.-]+\\.(m3u8|mpd|ts|m4s|mp4)") || name.contains(".."))
            throw AppException.badRequest("Invalid variant");
        Path path = uploads.variantDirectory(id).resolve(name);
        if (!Files.isRegularFile(path))
            throw AppException.notFound("Variant", name);
        String type = name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl"
                : name.endsWith(".mpd") ? "application/dash+xml" : name.endsWith(".ts") ? "video/mp2t" : "video/mp4";
        Resource resource = new FileSystemResource(path);
        if (name.endsWith(".m3u8") || name.endsWith(".mpd")) {
            String url = user == null ? "?viewer=" + viewer + "&expires=" + expires + "&signature=" + signature
                    : uploads.variantUrl(id, user.id(), name);
            String query = url.substring(url.indexOf('?'));
            String manifest = Files.readString(path);
            if (name.endsWith(".m3u8")) {
                StringBuilder out = new StringBuilder();
                for (String line : manifest.split("\\R")) {
                    out.append(line.isBlank() || line.startsWith("#") ? line : line + query).append('\n');
                }
                manifest = out.toString();
            } else {
                java.util.regex.Pattern attributes = java.util.regex.Pattern
                        .compile("(media|initialization)=\"([^\"]+)\"");
                java.util.regex.Matcher matcher = attributes.matcher(manifest);
                StringBuffer out = new StringBuffer();
                while (matcher.find())
                    matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                            matcher.group(1) + "=\"" + matcher.group(2) + query.replace("&", "&amp;") + "\""));
                matcher.appendTail(out);
                manifest = out.toString();
            }
            resource = new ByteArrayResource(manifest.getBytes(StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(resource);
    }

    private UUID require(AuthenticatedUser user) {
        if (user == null)
            throw AppException.unauthorized("Sign in to access media");
        return user.id();
    }

    public record Proof(String leafHash, String merkleRoot, List<Map<String, String>> siblings) {
    }

    public record Batch(List<UUID> ids) {
    }
}
