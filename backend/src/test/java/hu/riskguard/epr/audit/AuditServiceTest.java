package hu.riskguard.epr.audit;

import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.audit.internal.AggregationAuditRepository;
import hu.riskguard.epr.audit.internal.RegistryAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditService}.
 *
 * <p>Covers: (a) facade delegation — every field flows through to the repository;
 * (b) Micrometer counter increments with the correct {@code source} tag;
 * (c) null-guards at the facade (null event rejected before DB contact);
 * (d) page/size clamping on the read path.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private RegistryAuditRepository repository;

    @Mock
    private AggregationAuditRepository aggregationAuditRepository;

    private MeterRegistry meterRegistry;
    private AuditService auditService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auditService = new AuditService(repository, aggregationAuditRepository, meterRegistry);
    }

    @Test
    void recordRegistryFieldChange_passesAllFieldsToRepository() {
        FieldChangeEvent event = new FieldChangeEvent(
                PRODUCT_ID, TENANT_ID, "components[42].kf_code",
                "11010101", "11020202", USER_ID,
                AuditSource.AI_SUGGESTED_CONFIRMED,
                "prefix-match", "v1.2");

        auditService.recordRegistryFieldChange(event);

        verify(repository).insertAuditRow(
                eq(PRODUCT_ID), eq(TENANT_ID), eq("components[42].kf_code"),
                eq("11010101"), eq("11020202"),
                eq(USER_ID), eq(AuditSource.AI_SUGGESTED_CONFIRMED),
                eq("prefix-match"), eq("v1.2"));
    }

    @Test
    void recordRegistryFieldChange_incrementsCounterForEachSource() {
        for (AuditSource source : AuditSource.values()) {
            FieldChangeEvent event = new FieldChangeEvent(
                    PRODUCT_ID, TENANT_ID, "status",
                    null, "ACTIVE", USER_ID, source, null, null);

            auditService.recordRegistryFieldChange(event);

            double count = meterRegistry.get("audit.writes")
                    .tag("source", source.name()).counter().count();
            assertThat(count).as("counter for %s", source).isEqualTo(1.0);
        }
    }

    @Test
    void recordRegistryFieldChange_rejectsNullEvent() {
        assertThatThrownBy(() -> auditService.recordRegistryFieldChange(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("event must not be null");
    }

    @Test
    void fieldChangeEvent_rejectsNullSource() {
        assertThatThrownBy(() -> new FieldChangeEvent(
                PRODUCT_ID, TENANT_ID, "status",
                null, "ACTIVE", USER_ID, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source");
    }

    @Test
    void fieldChangeEvent_rejectsBlankFieldChanged() {
        assertThatThrownBy(() -> new FieldChangeEvent(
                PRODUCT_ID, TENANT_ID, "   ",
                null, "x", USER_ID, AuditSource.MANUAL, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fieldChanged");
    }

    @Test
    void listRegistryEntryAudit_clampsNegativePageToZero() {
        when(repository.listAuditByProduct(eq(PRODUCT_ID), eq(TENANT_ID), anyInt(), anyInt()))
                .thenReturn(List.of());

        auditService.listRegistryEntryAudit(PRODUCT_ID, TENANT_ID, -5, 50);

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(repository).listAuditByProduct(
                eq(PRODUCT_ID), eq(TENANT_ID), pageCaptor.capture(), anyInt());
        assertThat(pageCaptor.getValue()).isZero();
    }

    @Test
    void listRegistryEntryAudit_clampsOversizedSizeToMax() {
        when(repository.listAuditByProduct(eq(PRODUCT_ID), eq(TENANT_ID), anyInt(), anyInt()))
                .thenReturn(List.of());

        auditService.listRegistryEntryAudit(PRODUCT_ID, TENANT_ID, 0, 10_000);

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(repository).listAuditByProduct(
                eq(PRODUCT_ID), eq(TENANT_ID), anyInt(), sizeCaptor.capture());
        assertThat(sizeCaptor.getValue()).isEqualTo(500);
    }

    @Test
    void listRegistryEntryAudit_clampsNonPositiveSizeToOne() {
        when(repository.listAuditByProduct(eq(PRODUCT_ID), eq(TENANT_ID), anyInt(), anyInt()))
                .thenReturn(List.of());

        auditService.listRegistryEntryAudit(PRODUCT_ID, TENANT_ID, 0, 0);

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(repository).listAuditByProduct(
                eq(PRODUCT_ID), eq(TENANT_ID), anyInt(), sizeCaptor.capture());
        assertThat(sizeCaptor.getValue()).isEqualTo(1);
    }

    @Test
    void countRegistryEntryAudit_delegatesToRepository() {
        when(repository.countAuditByProduct(PRODUCT_ID, TENANT_ID)).thenReturn(42L);

        long result = auditService.countRegistryEntryAudit(PRODUCT_ID, TENANT_ID);

        assertThat(result).isEqualTo(42L);
    }
}
