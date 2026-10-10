package com.dbgram.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConnectionTest {
    @Test void convertsGeneratedProviderCredentialsAndRequiresTlsWhenConfigured() {
        DatabaseConnection connection = DatabaseConnection.fromUri("postgresql://dbgram:generated@internal-db/dbgram", "require");
        assertEquals("jdbc:postgresql://internal-db:5432/dbgram?sslmode=require", connection.jdbcUrl());
        assertEquals("dbgram", connection.username());
        assertEquals("generated", connection.password());
        assertFalse(connection.toString().contains("generated"));
    }
    @Test void decodesReservedCredentialsWithoutLosingLiteralPlus() {
        DatabaseConnection connection = DatabaseConnection.fromUri("postgres://u%3Aser:pa%40ss%3Aword+%2F%25@db.example:6543/dbgram", null);
        assertEquals("u:ser", connection.username());
        assertEquals("pa@ss:word+/%", connection.password());
        assertFalse(connection.jdbcUrl().contains(connection.password()));
    }
    @Test void handlesIpv6AndCommonConnectionOptions() {
        DatabaseConnection connection = DatabaseConnection.fromUri("postgres://u:p@[::1]/db?sslmode=disable&connect_timeout=10&application_name=DBgram", null);
        assertEquals("jdbc:postgresql://[::1]:5432/db?sslmode=disable&connectTimeout=10&ApplicationName=DBgram", connection.jdbcUrl());
    }
    @Test void explicitTlsSettingOverridesWeakerUriSetting() {
        assertTrue(DatabaseConnection.fromUri("postgres://u:p@host/db?sslmode=disable", "require").jdbcUrl().endsWith("sslmode=require"));
    }
    @Test void rejectsMalformedInputsAndCredentialOverridesWithoutExposingSecrets() {
        for (String uri : new String[]{"https://u:secret@host/db", "postgres://host/db", "postgres://u:secret@host/",
            "postgres://u:secret@host/db/other", "postgres://u:secret@host/db#fragment", "postgres://u:secret@host:99999/db",
            "postgres://u:secret@host/db?password=other", "postgres://u:secret@host/db?sslmode=wrong", "postgres://u:sec%zzret@host/db"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> DatabaseConnection.fromUri(uri, null));
            assertFalse(error.getMessage().contains("secret"));
            assertNull(error.getCause());
        }
    }
    @Test void environmentProcessorOverridesDevelopmentDatasourceDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("DATABASE_URL", "postgres://cloud:secret@render-db/dbgram",
            "DATABASE_SSL_MODE", "require", "spring.datasource.username", "development")));
        new DatabaseEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        assertEquals("cloud", environment.getProperty("spring.datasource.username"));
        assertEquals("secret", environment.getProperty("spring.datasource.password"));
        assertEquals("jdbc:postgresql://render-db:5432/dbgram?sslmode=require", environment.getProperty("spring.datasource.url"));
    }
    @Test void environmentProcessorPreservesExistingJdbcConfiguration() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("DATABASE_URL", "jdbc:postgresql://localhost/dbgram",
            "spring.datasource.username", "local")));
        new DatabaseEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        assertEquals("local", environment.getProperty("spring.datasource.username"));
        assertFalse(environment.getPropertySources().contains("providerDatabase"));
    }
}
