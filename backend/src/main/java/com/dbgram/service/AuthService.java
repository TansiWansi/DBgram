package com.dbgram.service;

import com.dbgram.domain.user.RefreshToken;
import com.dbgram.domain.user.User;
import com.dbgram.repository.RefreshTokenRepository;
import com.dbgram.repository.UserRepository;
import com.dbgram.security.JwtTokenProvider;
import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Authentication service — handles registration, login, token refresh, and logout.
 * Refresh tokens are stored hashed (SHA-256) in the database for security.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    /**
     * Register a new user — returns access + refresh token pair.
     */
    @Transactional
    public AuthTokens register(String username, String email, String displayName,
                               String password, String phone) {
        username = username.trim().toLowerCase(java.util.Locale.ROOT);
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!username.matches("[a-z0-9_]{3,30}")) throw AppException.badRequest("Username must use 3-30 letters, numbers, or underscores");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw AppException.badRequest("Password must be at most 72 UTF-8 bytes");
        if (phone != null && phone.isBlank()) phone = null;
        if (userRepository.existsByUsername(username)) {
            throw AppException.conflict("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw AppException.conflict("Email '" + email + "' is already registered");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(password))
                .phone(phone)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} ({})", username, user.getId());

        return generateTokens(user, null);
    }

    /**
     * Login with username/email + password — returns access + refresh token pair.
     */
    @Transactional
    public AuthTokens login(String usernameOrEmail, String password, String deviceInfo) {
        usernameOrEmail = usernameOrEmail.trim().toLowerCase(java.util.Locale.ROOT);
        final String login = usernameOrEmail;
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(login))
                .orElseThrow(() -> AppException.unauthorized("Invalid credentials"));

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw AppException.forbidden("Account is " + user.getStatus().name().toLowerCase());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw AppException.unauthorized("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateTokens(user, deviceInfo);
    }

    /**
     * Refresh an access token using a valid refresh token.
     */
    @Transactional
    public AuthTokens refreshAccessToken(String refreshToken) {
        String hash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> AppException.unauthorized("Invalid refresh token"));

        if (!stored.isValid()) {
            throw AppException.unauthorized("Refresh token expired or revoked");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> AppException.unauthorized("User not found"));
        if (user.getStatus() != User.UserStatus.ACTIVE) throw AppException.unauthorized("Account unavailable");

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return generateTokens(user, stored.getDeviceInfo());
    }

    /**
     * Logout — revoke all refresh tokens for the user.
     */
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    // ── Internal Helpers ──

    private AuthTokens generateTokens(User user, String deviceInfo) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String rawRefreshToken = tokenProvider.generateRefreshToken();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(rawRefreshToken))
                .deviceInfo(deviceInfo)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthTokens(accessToken, rawRefreshToken, user.getId(), user.getUsername());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Token pair returned after authentication.
     */
    public record AuthTokens(
            String accessToken,
            String refreshToken,
            UUID userId,
            String username
    ) {}
}
