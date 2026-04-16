package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import hu.riskguard.epr.registry.domain.ClassifierUsageSummary;
import hu.riskguard.epr.registry.internal.ClassifierUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClassifierUsageService}.
 * Verifies cap enforcement, usage increment delegation, and cross-tenant listing.
 */
@ExtendWith(MockitoExtension.class)
class ClassifierUsageServiceTest {

    @Mock private ClassifierUsageRepository repository;

    private ClassifierUsageService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClassifierUsageService(repository, 1000);
    }

    // ─── Test 1: isCapExceeded delegates to repository with correct year_month ─

    @Test
    void isCapExceeded_delegatesToRepositoryWithYearMonth() {
        when(repository.isCapExceeded(eq(TENANT_ID), anyString(), eq(1000))).thenReturn(false);

        boolean result = service.isCapExceeded(TENANT_ID);

        assertThat(result).isFalse();
        ArgumentCaptor<String> yearMonthCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).isCapExceeded(eq(TENANT_ID), yearMonthCaptor.capture(), eq(1000));
        // Year-month format: yyyy-MM (e.g. "2026-04")
        assertThat(yearMonthCaptor.getValue()).matches("\\d{4}-\\d{2}");
    }

    // ─── Test 2: isCapExceeded returns true when repository says exceeded ──────

    @Test
    void isCapExceeded_returnsTrueWhenRepositoryExceeded() {
        when(repository.isCapExceeded(eq(TENANT_ID), anyString(), eq(1000))).thenReturn(true);

        assertThat(service.isCapExceeded(TENANT_ID)).isTrue();
    }

    // ─── Test 3: incrementUsage delegates upsertIncrement with current month ──

    @Test
    void incrementUsage_delegatesUpsertIncrementWithCurrentMonthAndTokenCounts() {
        service.incrementUsage(TENANT_ID, 120, 45);

        ArgumentCaptor<String> yearMonthCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).upsertIncrement(eq(TENANT_ID), yearMonthCaptor.capture(), eq(120), eq(45));
        assertThat(yearMonthCaptor.getValue()).matches("\\d{4}-\\d{2}");
    }

    // ─── Test 4: getAllTenantsUsage returns repository result ─────────────────

    @Test
    void getAllTenantsUsage_returnsSummaryList() {
        List<ClassifierUsageSummary> summaries = List.of(
                new ClassifierUsageSummary(TENANT_ID, "Tenant A", 42, 5040, 1890),
                new ClassifierUsageSummary(UUID.randomUUID(), "Tenant B", 7, 840, 315)
        );
        when(repository.findAllForMonth(anyString())).thenReturn(summaries);

        List<ClassifierUsageSummary> result = service.getAllTenantsUsage();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).callCount()).isEqualTo(42);
        assertThat(result.get(0).inputTokens()).isEqualTo(5040);
        assertThat(result.get(0).outputTokens()).isEqualTo(1890);
    }
}
