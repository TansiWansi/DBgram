package com.dbgram.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;

/**
 * Render and Railway supply PostgreSQL URIs; local development can keep using
 * JDBC URLs.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String value = environment.getProperty("DATABASE_URL");
        if (value == null || value.isBlank() || value.startsWith("jdbc:postgresql:"))
            return;
        DatabaseConnection connection = DatabaseConnection.fromUri(value.trim(),
                environment.getProperty("DATABASE_SSL_MODE"));
        environment.getPropertySources().addFirst(new MapPropertySource("providerDatabase", Map.of(
                "spring.datasource.url", connection.jdbcUrl(), "spring.datasource.username", connection.username(),
                "spring.datasource.password", connection.password())));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
