package com.dinoventures.wallet.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Normalizes the JDBC datasource URL before any Spring beans are created.
 *
 * Cloud providers (Render, Railway, Heroku) supply connection strings in the
 * format  postgresql://user:pass@host/db
 * but HikariCP / the PostgreSQL JDBC driver requires a plain host URL:
 *         jdbc:postgresql://host/db
 * with credentials supplied separately via spring.datasource.username/password.
 *
 * This processor does two things:
 *   1. Adds the "jdbc:" prefix when missing.
 *   2. Strips embedded "user:pass@" credentials from the URL so the driver
 *      never sees duplicate credentials (URL + separate properties).
 *
 * Running as an EnvironmentPostProcessor means Spring Boot's own HikariCP
 * auto-configuration sees the already-corrected URL — no custom DataSource
 * bean needed, no driver registration fighting.
 *
 * Registered in META-INF/spring.factories.
 */
public class JdbcUrlNormalizer implements EnvironmentPostProcessor {

    private static final String PROPERTY = "spring.datasource.url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String url = environment.getProperty(PROPERTY);
        if (url == null) return;

        // 1. Add jdbc: prefix if absent (Render provides "postgresql://..." format)
        if (!url.startsWith("jdbc:")) {
            url = "jdbc:" + url;
        }

        // 2. Strip embedded credentials — "jdbc:postgresql://user:pass@host/db"
        //    becomes "jdbc:postgresql://host/db".
        //    HikariCP uses spring.datasource.username/password for auth instead.
        url = stripCredentials(url);

        environment.getPropertySources().addFirst(
                new MapPropertySource("normalizedJdbcUrl", Map.of(PROPERTY, url))
        );
    }

    /**
     * Removes the "user:pass@" segment from a JDBC URL if present.
     * jdbc:postgresql://user:pass@host:5432/db  →  jdbc:postgresql://host:5432/db
     */
    private static String stripCredentials(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd == -1) return url;

        String scheme = url.substring(0, schemeEnd + 3); // e.g. "jdbc:postgresql://"
        String rest   = url.substring(schemeEnd + 3);    // e.g. "user:pass@host/db"

        int atSign = rest.indexOf('@');
        if (atSign == -1) return url;                    // no embedded credentials

        return scheme + rest.substring(atSign + 1);
    }
}
