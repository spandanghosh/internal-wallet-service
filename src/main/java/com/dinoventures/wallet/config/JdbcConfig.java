package com.dinoventures.wallet.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class JdbcConfig {

    /**
     * Custom DataSource bean that normalizes the JDBC URL.
     *
     * Cloud providers (Render, Railway, Heroku) provide connection strings in
     * the format postgresql://... but the PostgreSQL JDBC driver requires the
     * jdbc:postgresql:// prefix. This bean handles both formats transparently.
     */
    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.hikari.maximum-pool-size:20}") int maxPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:2}") int minIdle) {

        // Normalize URL: add jdbc: prefix if the cloud provider omitted it
        if (url != null && !url.startsWith("jdbc:")) {
            url = "jdbc:" + url;
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");  // must be explicit when bypassing Spring Boot auto-config
        config.setJdbcUrl(url);
        if (username != null && !username.isEmpty()) config.setUsername(username);
        if (password != null && !password.isEmpty()) config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("WalletHikariPool");

        return new HikariDataSource(config);
    }

    /**
     * NamedParameterJdbcTemplate enables :paramName style SQL parameters
     * instead of positional ?. Makes complex queries more readable.
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * Transaction manager for @Transactional support.
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
