package hu.riskguard.epr.registry.domain;

import hu.riskguard.epr.registry.internal.ClassifierUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Service for AI classifier monthly cap enforcement and cost metering.
 *
 * <p>Cap: {@code riskguard.classifier.monthly-cap} (default 1000 calls/tenant/month).
 * Cost: 0.15 Ft per Gemini call (CP-5 §4.4 cost breakdown).
 */
@Service
public class ClassifierUsageService {

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");

    private final ClassifierUsageRepository repository;
    private final int monthlyCap;

    public ClassifierUsageService(
            ClassifierUsageRepository repository,
            @Value("${risk-guard.classifier.monthly-cap:1000}") int monthlyCap) {
        this.repository = repository;
        this.monthlyCap = monthlyCap;
    }

    /**
     * Returns true when the tenant has reached or exceeded the monthly cap for the current month.
     */
    @Transactional(readOnly = true)
    public boolean isCapExceeded(UUID tenantId) {
        return repository.isCapExceeded(tenantId, currentYearMonth(), monthlyCap);
    }

    /**
     * Returns the tenant's classifier call count for the current Europe/Budapest calendar month
     * (0 when no row has been written yet). Story 10.3 batch endpoint reads this for the cap pre-check
     * and for the post-batch {@code ClassifierUsageInfo} response.
     */
    @Transactional(readOnly = true)
    public int getCurrentMonthCallCount(UUID tenantId) {
        return repository.getCallCountForMonth(tenantId, currentYearMonth());
    }

    /** Configured monthly cap (calls/tenant/month). Used by the batch controller for pre-checks. */
    public int getMonthlyCap() {
        return monthlyCap;
    }

    /**
     * Atomically increments the call count and accumulates token counts for the current month.
     */
    @Transactional
    public void incrementUsage(UUID tenantId, int inputTokens, int outputTokens) {
        repository.upsertIncrement(tenantId, currentYearMonth(), inputTokens, outputTokens);
    }

    /**
     * Returns all tenants' usage for the current month, ordered by call_count DESC.
     * For PLATFORM_ADMIN use only — cross-tenant result.
     */
    @Transactional(readOnly = true)
    public List<ClassifierUsageSummary> getAllTenantsUsage() {
        return repository.findAllForMonth(currentYearMonth());
    }

    private String currentYearMonth() {
        return LocalDate.now(BUDAPEST).format(YEAR_MONTH_FMT);
    }
}
