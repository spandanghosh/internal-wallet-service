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
 * but the PostgreSQL JDBC driver requires
 *         jdbc:postgresql://user:pass@host/db
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
        if (url != null && !url.startsWith("jdbc:")) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("normalizedJdbcUrl",
                            Map.of(PROPERTY, "jdbc:" + url))
            );
        }
    }
}
