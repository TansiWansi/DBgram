package com.dbgram.util;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Application-level exception with HTTP status and machine-readable error code.
 * Caught by {@link GlobalExceptionHandler} and converted to {@link ApiResponse}.
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AppException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    // ── Factory Methods ──

    public static AppException notFound(String entity, Object id) {
        return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                entity + " not found with id: " + id);
    }

    public static AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static AppException unauthorized(String message) {
        return new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static AppException internal(String message) {
        return new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message);
    }
}
