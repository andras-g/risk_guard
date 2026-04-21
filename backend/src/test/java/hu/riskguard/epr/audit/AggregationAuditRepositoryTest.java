package hu.riskguard.epr.audit;

import hu.riskguard.epr.audit.internal.AggregationAuditRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip insert + read tests for {@link AggregationAuditRepository} (Story 10.8 AC #33).
 *
 * <p>Verifies all three event types persist correctly.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AggregationAuditRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    AggregationAuditRepository repository;

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END   = LocalDate.of(2026, 3, 31);

    @Test
    void insertAggregationRun_roundTrip() {
        UUID tenantId = UUID.randomUUID();

        repository.insertAggregationRun(tenantId, PERIOD_START, PERIOD_END, 1234L, 10, 2);

        List<AggregationAuditRepository.AggregationAuditRow> rows =
                repository.findByTenantAndEventType(tenantId, "AGGREGATION_RUN");
        assertThat(rows).hasSize(1);
        AggregationAuditRepository.AggregationAuditRow row = rows.get(0);
        assertThat(row.tenantId()).isEqualTo(tenantId);
        assertThat(row.eventType()).isEqualTo("AGGREGATION_RUN");
        assertThat(row.periodStart()).isEqualTo(PERIOD_START);
        assertThat(row.periodEnd()).isEqualTo(PERIOD_END);
        assertThat(row.durationMs()).isEqualTo(1234L);
        assertThat(row.resolvedCount()).isEqualTo(10);
        assertThat(row.unresolvedCount()).isEqualTo(2);
        assertThat(row.performedByUserId()).isNull();
        assertThat(row.page()).isNull();
        assertThat(row.pageSize()).isNull();
    }

    @Test
    void insertProvenanceFetch_roundTrip() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        repository.insertProvenanceFetch(tenantId, userId, PERIOD_START, PERIOD_END, 2, 50);

        List<AggregationAuditRepository.AggregationAuditRow> rows =
                repository.findByTenantAndEventType(tenantId, "PROVENANCE_FETCH");
        assertThat(rows).hasSize(1);
        AggregationAuditRepository.AggregationAuditRow row = rows.get(0);
        assertThat(row.eventType()).isEqualTo("PROVENANCE_FETCH");
        assertThat(row.performedByUserId()).isEqualTo(userId);
        assertThat(row.page()).isEqualTo(2);
        assertThat(row.pageSize()).isEqualTo(50);
        assertThat(row.durationMs()).isNull();
        assertThat(row.resolvedCount()).isNull();
    }

    @Test
    void insertCsvExport_roundTrip() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        repository.insertCsvExport(tenantId, userId, PERIOD_START, PERIOD_END);

        List<AggregationAuditRepository.AggregationAuditRow> rows =
                repository.findByTenantAndEventType(tenantId, "CSV_EXPORT");
        assertThat(rows).hasSize(1);
        AggregationAuditRepository.AggregationAuditRow row = rows.get(0);
        assertThat(row.eventType()).isEqualTo("CSV_EXPORT");
        assertThat(row.performedByUserId()).isEqualTo(userId);
        assertThat(row.page()).isNull();
        assertThat(row.pageSize()).isNull();
        assertThat(row.durationMs()).isNull();
    }

    @Test
    void countByTenantAndEventType_returnsCorrectCount() {
        UUID tenantId = UUID.randomUUID();

        repository.insertAggregationRun(tenantId, PERIOD_START, PERIOD_END, 100L, 5, 1);
        repository.insertAggregationRun(tenantId, PERIOD_START, PERIOD_END, 200L, 6, 0);

        assertThat(repository.countByTenantAndEventType(tenantId, "AGGREGATION_RUN")).isEqualTo(2);
        assertThat(repository.countByTenantAndEventType(tenantId, "PROVENANCE_FETCH")).isEqualTo(0);
    }
}
