package com.dbgram.controller;

import com.dbgram.security.AuthenticatedUser;
import com.dbgram.service.AuthService;
import com.dbgram.util.AppException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private final AuthService service = mock(AuthService.class);
    private final AuthController controller = new AuthController(service);
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(controller, "secure", true);
        ReflectionTestUtils.setField(controller, "allowedOrigins", "https://dbgram.example");
        var tokens = new AuthService.AuthTokens("access", "refresh", id, "alice");
        when(service.login(anyString(), anyString(), any())).thenReturn(tokens);
        when(service.refreshAccessToken("refresh")).thenReturn(tokens);
    }

    @Test
    void browserRefreshTokenIsHttpOnlyAndNotInJson() {
        var response = new MockHttpServletResponse();
        var result = controller.login(
                new AuthController.LoginRequest("alice", "password123", null),
                new MockHttpServletRequest(), response);

        assertFalse(result.getData().containsKey("refreshToken"));
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void nativeClientReceivesRotatingTokenForSecureStorage() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Client-Type", "native");
        var result = controller.login(
                new AuthController.LoginRequest("alice", "password123", null),
                request, new MockHttpServletResponse());

        assertEquals("refresh", result.getData().get("refreshToken"));
    }

    @Test
    void logoutUsesAuthenticatedIdentity() {
        controller.logout(new AuthenticatedUser(id, "alice"), new MockHttpServletResponse());
        verify(service).logout(id);
    }

    @Test
    void cookieRefreshRejectsUntrustedOrigin() {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://attacker.example");

        assertThrows(AppException.class, () -> controller.refresh(
                null, "refresh", request, new MockHttpServletResponse()));
        verify(service, never()).refreshAccessToken(anyString());
    }

    @Test
    void cookieRefreshAcceptsSameOriginLanAddressWithoutAnAllowlistEntry() {
        var request = lanRequest("http://192.0.2.10:8787");
        var response = new MockHttpServletResponse();
        var result = controller.refresh(null, "refresh", request, response);

        assertEquals(id, result.getData().get("userId"));
        assertFalse(result.getData().containsKey("refreshToken"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(service).refreshAccessToken("refresh");
    }

    @Test
    void cookieRefreshStillAcceptsExplicitlyAllowedCrossOriginClients() {
        var request = lanRequest("https://dbgram.example");
        var result = controller.refresh(null, "refresh", request, new MockHttpServletResponse());

        assertEquals(id, result.getData().get("userId"));
        verify(service).refreshAccessToken("refresh");
    }

    @Test
    void cookieRefreshRequiresBothSchemeAndPortToMatch() {
        for (String origin : List.of("http://192.0.2.10:9999", "https://192.0.2.10:8787")) {
            var request = lanRequest(origin);
            assertThrows(AppException.class, () -> controller.refresh(
                    null, "refresh", request, new MockHttpServletResponse()));
        }
        verify(service, never()).refreshAccessToken(anyString());
    }

    private MockHttpServletRequest lanRequest(String origin) {
        var request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("192.0.2.10");
        request.setServerPort(8787);
        request.addHeader("Origin", origin);
        return request;
    }
}
