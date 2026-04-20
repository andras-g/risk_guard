package hu.riskguard.epr.audit;

import hu.riskguard.epr.audit.events.FieldChangeEvent;
import hu.riskguard.epr.audit.internal.RegistryAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Story 10.4 AC #22 — {@link AuditService#recordRegistryBootstrapBatch} unit tests.
 *
 * <p>Verifies: (a) 1001 events produce 3 JDBC round-trips (ceil(1001/500) = 3);
 * (b) each event increments the NAV_BOOTSTRAP counter; (c) empty/null input is a no-op.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceBootstrapBatchTest {

    @Mock
    private RegistryAuditRepository repository;

    private MeterRegistry meterRegistry;
    private AuditService auditService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auditService = new AuditService(repository, meterRegistry);
    }

    @Test
    void batch1001Events_produces3JdbcRoundTrips() {
        List<FieldChangeEvent> events = makeEvents(1001);

        auditService.recordRegistryBootstrapBatch(events);

        // ceil(1001 / 500) = 3 calls to insertAuditRowBatch
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FieldChangeEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(3)).insertAuditRowBatch(captor.capture());

        List<List<FieldChangeEvent>> batches = captor.getAllValues();
        assertThat(batches.get(0)).hasSize(500);
        assertThat(batches.get(1)).hasSize(500);
        assertThat(batches.get(2)).hasSize(1);
    }

    @Test
    void batch1001Events_incrementsNavBootstrapCounter1001Times() {
        List<FieldChangeEvent> events = makeEvents(1001);

        auditService.recordRegistryBootstrapBatch(events);

        double count = meterRegistry.get("audit.writes")
                .tag("source", AuditSource.NAV_BOOTSTRAP.name())
                .counter().count();
        assertThat(count).isEqualTo(1001.0);
    }

    @Test
    void batchWithNullInput_isNoOp() {
        auditService.recordRegistryBootstrapBatch(null);
        verify(repository, never()).insertAuditRowBatch(anyList());
    }

    @Test
    void batchWithEmptyList_isNoOp() {
        auditService.recordRegistryBootstrapBatch(List.of());
        verify(repository, never()).insertAuditRowBatch(anyList());
    }

    @Test
    void batchExactlyOneBatch_produces1RoundTrip() {
        List<FieldChangeEvent> events = makeEvents(500);

        auditService.recordRegistryBootstrapBatch(events);

        verify(repository, times(1)).insertAuditRowBatch(anyList());
    }

    private List<FieldChangeEvent> makeEvents(int count) {
        List<FieldChangeEvent> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(new FieldChangeEvent(
                    PRODUCT_ID, TENANT_ID, "bootstrap.created",
                    null, "vtsz=1234|name=Test|source=NAV_BOOTSTRAP",
                    USER_ID, AuditSource.NAV_BOOTSTRAP, null, null));
        }
        return events;
    }
}
