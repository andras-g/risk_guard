package hu.riskguard.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseTlsHealthIndicatorTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    DatabaseTlsHealthIndicator indicator;

    @Test
    void health_returnsUp_whenSslIsUsed() {
        when(jdbcTemplate.queryForObject("SELECT ssl_is_used()", Boolean.class))
                .thenReturn(Boolean.TRUE);
        when(jdbcTemplate.queryForList(
                "SELECT version, cipher FROM pg_stat_ssl WHERE pid = pg_backend_pid()"))
                .thenReturn(List.of(Map.of("version", "TLSv1.3", "cipher", "TLS_AES_256_GCM_SHA384")));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ssl", true);
        assertThat(health.getDetails()).containsEntry("tlsVersion", "TLSv1.3");
        assertThat(health.getDetails()).containsEntry("cipher", "TLS_AES_256_GCM_SHA384");
    }

    @Test
    void health_returnsUp_withUnknownTls_whenPgStatSslHasNoRow() {
        when(jdbcTemplate.queryForObject("SELECT ssl_is_used()", Boolean.class))
                .thenReturn(Boolean.TRUE);
        when(jdbcTemplate.queryForList(
                "SELECT version, cipher FROM pg_stat_ssl WHERE pid = pg_backend_pid()"))
                .thenReturn(Collections.emptyList());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ssl", true);
        assertThat(health.getDetails()).containsEntry("tlsVersion", "unknown");
        assertThat(health.getDetails()).containsEntry("cipher", "unknown");
        assertThat(health.getDetails())
                .containsKey("note")
                .extractingByKey("note")
                .asString()
                .isNotEmpty();
    }

    @Test
    void health_returnsDown_whenSslIsNotUsed() {
        when(jdbcTemplate.queryForObject("SELECT ssl_is_used()", Boolean.class))
                .thenReturn(Boolean.FALSE);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("ssl", false);
        assertThat(health.getDetails())
                .containsKey("reason")
                .extractingByKey("reason")
                .asString()
                .isNotEmpty();
    }

    @Test
    void health_returnsDown_whenQueryFails() {
        when(jdbcTemplate.queryForObject("SELECT ssl_is_used()", Boolean.class))
                .thenThrow(new RuntimeException("Connection failed"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsKey("reason")
                .extractingByKey("reason")
                .asString()
                .isNotEmpty();
    }
}
