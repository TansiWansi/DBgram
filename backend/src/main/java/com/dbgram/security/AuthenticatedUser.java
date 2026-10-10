package com.dbgram.security;

import java.util.UUID;

/**
 * Lightweight principal stored in SecurityContext after JWT validation.
 * Avoids loading the full User entity on every authenticated request.
 */
public record AuthenticatedUser(UUID id, String username) {
}
