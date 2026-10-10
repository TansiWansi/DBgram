package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.AuthService;
import com.dbgram.util.ApiResponse;
import com.dbgram.util.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.CorsUtils;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @Value("${app.cookie-secure:true}")
    private boolean secure;
    @Value("${app.cors-origins}")
    private String allowedOrigins;

    @PostMapping("/register")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req,
            HttpServletResponse res) {
        return tokens(authService.register(r.username(), r.email(), r.displayName(), r.password(), r.phone()), req,
                res);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest r, HttpServletRequest req,
            HttpServletResponse res) {
        return tokens(authService.login(r.usernameOrEmail(), r.password(), r.deviceInfo()), req, res);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody(required = false) RefreshRequest body,
            @CookieValue(name = "dbgram_refresh", required = false) String cookie, HttpServletRequest req,
            HttpServletResponse res) {
        String token = body != null && body.refreshToken() != null ? body.refreshToken() : cookie;
        if (token == null || token.isBlank()) {
            throw AppException.unauthorized("No refresh session");
        }
        String origin = req.getHeader("Origin");
        // A same-origin proxy can be opened through any LAN hostname or address.
        // Separate browser origins still require the explicit CORS allowlist.
        if (CorsUtils.isCorsRequest(req) && Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .noneMatch(origin::equals)) {
            throw AppException.forbidden("Origin is not allowed");
        }
        return tokens(authService.refreshAccessToken(token), req, res);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletResponse res) {
        if (principal == null)
            throw AppException.unauthorized("Authentication required");
        authService.logout(principal.id());
        cookie(res, "", Duration.ZERO);
        return ApiResponse.ok("Logged out", null);
    }

    private ApiResponse<Map<String, Object>> tokens(AuthService.AuthTokens t, HttpServletRequest req,
            HttpServletResponse res) {
        cookie(res, t.refreshToken(), Duration.ofDays(30));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", t.accessToken());
        result.put("userId", t.userId());
        result.put("username", t.username());
        if ("native".equalsIgnoreCase(req.getHeader("X-Client-Type")))
            result.put("refreshToken", t.refreshToken());
        res.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(result);
    }

    private void cookie(HttpServletResponse res, String token, Duration age) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("dbgram_refresh", token).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/api/auth").maxAge(age).build().toString());
    }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 30) String username,
            @NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password, @Size(max = 20) String phone) {
    }

    public record LoginRequest(@NotBlank @Size(max = 255) String usernameOrEmail,
            @NotBlank @Size(max = 128) String password, @Size(max = 255) String deviceInfo) {
    }

    public record RefreshRequest(@Size(max = 512) String refreshToken) {
    }
}
