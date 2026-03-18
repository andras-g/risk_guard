package hu.riskguard.core.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Health indicator that verifies the active database connection is TLS-encrypted.
 * Queries PostgreSQL system views {@code ssl_is_used()} and {@code pg_stat_ssl}
 * to report the TLS version and cipher suite.
 *
 * <p>Reports {@code UP} when the connection uses TLS, {@code DOWN} otherwise
 * (misconfiguration or missing Cloud SQL Auth Proxy).
 *
 * <p>Uses {@link JdbcTemplate} (not jOOQ) because {@code pg_stat_ssl} is a
 * PostgreSQL system view outside jOOQ codegen scope.
 */
@Component
public class DatabaseTlsHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTlsHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Boolean sslUsed = jdbcTemplate.queryForObject(
                    "SELECT ssl_is_used()", Boolean.class);

            if (Boolean.TRUE.equals(sslUsed)) {
                // Use query() instead of queryForMap() to handle the edge case where
                // pg_stat_ssl has no row for the current backend PID (e.g., PgBouncer
                // or connection-pool reuse). queryForMap() would throw
                // EmptyResultDataAccessException, causing a false-negative DOWN report.
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT version, cipher FROM pg_stat_ssl WHERE pid = pg_backend_pid()");

                if (rows.isEmpty()) {
                    return Health.up()
                            .withDetail("ssl", true)
                            .withDetail("tlsVersion", "unknown")
                            .withDetail("cipher", "unknown")
                            .withDetail("note", "ssl_is_used()=true but pg_stat_ssl has no row for current pid")
                            .build();
                }

                Map<String, Object> sslInfo = rows.getFirst();
                return Health.up()
                        .withDetail("ssl", true)
                        .withDetail("tlsVersion", sslInfo.get("version"))
                        .withDetail("cipher", sslInfo.get("cipher"))
                        .build();
            } else {
                return Health.down()
                        .withDetail("ssl", false)
                        .withDetail("reason",
                                "Database connection is not using TLS — check Cloud SQL Auth Proxy setup")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("reason", "Failed to query pg_stat_ssl")
                    .build();
        }
    }
}
