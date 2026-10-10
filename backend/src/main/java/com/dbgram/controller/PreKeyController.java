package com.dbgram.controller;

import com.dbgram.domain.messaging.CassandraPreKeyBundle;
import com.dbgram.repository.PreKeyBundleRepository;
import com.dbgram.security.AuthenticatedUser;
import com.dbgram.util.ApiResponse;
import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * E2EE PreKey Controller for the Signal Protocol implementation.
 */
@org.springframework.context.annotation.Profile("distributed")
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class PreKeyController {

    private final PreKeyBundleRepository preKeyBundleRepository;

    @PostMapping("/bundle")
    public ResponseEntity<ApiResponse<Void>> uploadPreKeyBundle(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody CassandraPreKeyBundle bundle) {
        
        bundle.setUserId(principal.id());
        preKeyBundleRepository.save(bundle);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("PreKey bundle uploaded", null));
    }

    @GetMapping("/{userId}/bundle")
    public ResponseEntity<ApiResponse<CassandraPreKeyBundle>> getPreKeyBundle(
            @PathVariable UUID userId) {
        
        CassandraPreKeyBundle bundle = preKeyBundleRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("PreKeyBundle", userId));
                
        return ResponseEntity.ok(ApiResponse.ok(bundle));
    }
}
