package hu.riskguard.identity.domain.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class TenantContextSwitchedEventListenerTest {

    private final TenantContextSwitchedEventListener listener = new TenantContextSwitchedEventListener();

    @Test
    void shouldHandleTenantContextSwitchedEventWithoutException() {
        // Given — email removed from event per PII zero-tolerance policy
        TenantContextSwitchedEvent event = TenantContextSwitchedEvent.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // When / Then — listener should process the event without errors
        assertThatCode(() -> listener.onTenantContextSwitched(event))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleEventWithSamePreviousAndNewTenant() {
        // Given — switching to the same tenant (e.g., returning to home context)
        UUID tenantId = UUID.randomUUID();
        TenantContextSwitchedEvent event = TenantContextSwitchedEvent.of(
                UUID.randomUUID(),
                tenantId,
                tenantId
        );

        // When / Then
        assertThatCode(() -> listener.onTenantContextSwitched(event))
                .doesNotThrowAnyException();
    }
}
