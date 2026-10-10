package com.dbgram.config;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Converts provider connection URIs without placing passwords in the JDBC URL
 * or logs.
 */
public record DatabaseConnection(String jdbcUrl, String username, String password) {
    private static final Set<String> SSL_MODES = Set.of("disable", "allow", "prefer", "require", "verify-ca",
            "verify-full");
    private static final Map<String, String> QUERY_KEYS = Map.of(
            "sslmode", "sslmode", "connect_timeout", "connectTimeout", "connectTimeout", "connectTimeout",
            "application_name", "ApplicationName", "ApplicationName", "ApplicationName",
            "socketTimeout", "socketTimeout", "currentSchema", "currentSchema");

    public static DatabaseConnection fromUri(String value, String sslOverride) {
        try {
            URI uri = URI.create(value);
            if (!Set.of("postgres", "postgresql").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getFragment() != null || uri.getRawUserInfo() == null)
                throw new IllegalArgumentException();
            String[] credentials = uri.getRawUserInfo().split(":", 2);
            if (credentials.length != 2 || credentials[0].isBlank() || credentials[1].isBlank())
                throw new IllegalArgumentException();
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.length() < 2 || decode(path.substring(1)).contains("/"))
                throw new IllegalArgumentException();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException();
            Map<String, String> parameters = new LinkedHashMap<>();
            if (uri.getRawQuery() != null)
                for (String pair : uri.getRawQuery().split("&")) {
                    String[] parts = pair.split("=", 2);
                    String key = QUERY_KEYS.get(decode(parts[0]));
                    if (key == null || parts.length != 2 || parameters.putIfAbsent(key, decode(parts[1])) != null)
                        throw new IllegalArgumentException();
                }
            String ssl = sslOverride == null || sslOverride.isBlank() ? parameters.getOrDefault("sslmode", "prefer")
                    : sslOverride;
            if (!SSL_MODES.contains(ssl))
                throw new IllegalArgumentException();
            parameters.put("sslmode", ssl);
            String query = parameters.entrySet().stream().map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining("&"));
            return new DatabaseConnection("jdbc:postgresql://" + uri.getHost() + ":" + port + path + "?" + query,
                    decode(credentials[0]), decode(credentials[1]));
        } catch (RuntimeException ignored) {
            // URI parser errors can contain the complete password-bearing input. Do not
            // attach the cause.
            throw new IllegalArgumentException(
                    "DATABASE_URL must be a valid PostgreSQL URI with username, password, hostname and database. Use DATABASE_SSL_MODE for TLS; use JDBC form for other driver options.");
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "DatabaseConnection[credentials redacted]";
    }
}
