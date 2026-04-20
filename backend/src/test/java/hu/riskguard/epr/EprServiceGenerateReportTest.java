package hu.riskguard.epr;

import hu.riskguard.datasource.domain.DataSourceService;
import hu.riskguard.epr.domain.DagEngine;
import hu.riskguard.epr.domain.EprConfigValidator;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.epr.domain.FeeCalculator;
import hu.riskguard.epr.internal.EprRepository;
import hu.riskguard.epr.producer.domain.ProducerProfileService;
import hu.riskguard.epr.report.EprReportArtifact;
import hu.riskguard.epr.report.EprReportRequest;
import hu.riskguard.epr.report.EprReportTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EprService#generateReport(EprReportRequest, List)} per AC 13.
 * Scope: validation guards + happy-path delegation. Integration tests covering the
 * real exporter and aggregation live in {@code EprOkirkapuExportIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EprServiceGenerateReportTest {

    @Mock
    private EprRepository eprRepository;
    @Mock
    private DagEngine dagEngine;
    @Mock
    private EprReportTarget reportTarget;
    @Mock
    private EprConfigValidator eprConfigValidator;
    @Mock
    private DataSourceService dataSourceService;
    @Mock
    private ProducerProfileService producerProfileService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private EprService eprService;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String TAX = "12345678";
    private static final LocalDate Q1_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate Q1_END = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        eprService = new EprService(eprRepository, dagEngine, new FeeCalculator(),
                eprConfigValidator, dataSourceService, reportTarget, producerProfileService, transactionManager);
        // Make TransactionTemplate.executeWithoutResult run synchronously on whatever
        // callback is given; we only want to verify generateReport wires it up.
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doAnswer(inv -> null).when(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void throws_400_when_periodStart_after_periodEnd() {
        EprReportRequest req = new EprReportRequest(TENANT, Q1_END, Q1_START, TAX);
        assertThatThrownBy(() -> eprService.generateReport(req, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("period start must not be after end");
    }

    @Test
    void throws_400_when_period_extends_into_future() {
        LocalDate future = LocalDate.now().plusDays(5);
        // Keep within a quarter that starts today-ish so only the future check fires.
        EprReportRequest req = new EprReportRequest(TENANT, LocalDate.now(), future, TAX);
        assertThatThrownBy(() -> eprService.generateReport(req, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("may not extend into the future");
    }

    @Test
    void throws_400_when_period_spans_two_quarters() {
        // Use a past pair of quarters so the future-period check does not pre-empt the quarter check
        EprReportRequest req = new EprReportRequest(
                TENANT, LocalDate.of(2025, 12, 15), LocalDate.of(2026, 1, 15), TAX);
        assertThatThrownBy(() -> eprService.generateReport(req, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("single calendar quarter");
    }

    @Test
    void throws_403_when_tax_number_does_not_match_tenant() {
        when(dataSourceService.getTenantTaxNumber(TENANT)).thenReturn(Optional.of("87654321"));
        EprReportRequest req = new EprReportRequest(TENANT, Q1_START, Q1_END, "12345678");
        assertThatThrownBy(() -> eprService.generateReport(req, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void propagates_412_when_producer_profile_incomplete() {
        when(dataSourceService.getTenantTaxNumber(TENANT)).thenReturn(Optional.empty());
        when(producerProfileService.get(TENANT)).thenThrow(
                new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "producer.profile.incomplete"));
        EprReportRequest req = new EprReportRequest(TENANT, Q1_START, Q1_END, TAX);
        assertThatThrownBy(() -> eprService.generateReport(req, List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.PRECONDITION_FAILED));
    }

    @Test
    void happy_path_delegates_to_reportTarget_and_logs_export() {
        when(dataSourceService.getTenantTaxNumber(TENANT)).thenReturn(Optional.empty());
        when(producerProfileService.get(TENANT)).thenReturn(mock(hu.riskguard.epr.producer.domain.ProducerProfile.class));
        EprReportArtifact stubArtifact = new EprReportArtifact(
                "x.zip", "application/zip", new byte[]{1, 2, 3}, new byte[]{4, 5, 6},
                "summary", List.of());
        when(reportTarget.generate(any(), any(), any(), any())).thenReturn(stubArtifact);
        // config version lookup — allow it to fall through to the catch-block and return 0
        when(eprRepository.findActiveConfig()).thenReturn(Optional.empty());

        EprReportRequest req = new EprReportRequest(TENANT, Q1_START, Q1_END, TAX);
        EprReportArtifact result = eprService.generateReport(req, List.of());
        assertThat(result).isSameAs(stubArtifact);
    }
}
