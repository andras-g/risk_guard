package hu.riskguard.epr.registry.api;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import hu.riskguard.core.util.JwtUtil;
import hu.riskguard.epr.registry.api.dto.BatchPackagingRequest;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResponse;
import hu.riskguard.epr.registry.api.dto.BatchPackagingResult;
import hu.riskguard.epr.registry.api.dto.ClassifierUsageInfo;
import hu.riskguard.epr.registry.api.exception.BatchConcurrencyLimitExceededException;
import hu.riskguard.epr.registry.api.exception.ClassifierCapExceededException;
import hu.riskguard.epr.registry.domain.BatchPackagingClassifierService;
import hu.riskguard.epr.registry.domain.BatchPackagingConcurrencyGate;
import hu.riskguard.epr.registry.domain.ClassifierUsageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Batch packaging classifier endpoint (Story 10.3).
 *
 * <p>{@code POST /api/v1/classifier/batch-packaging} — accepts 1–100 invoice-line
 * pairs and returns a per-pair packaging chain. Composes the existing Story 9.3
 * classifier router under bounded concurrency.
 *
 * <p>Gating order on every request:
 * <ol>
 *   <li>{@link TierRequired @TierRequired(PRO_EPR)} via {@code TierGateInterceptor}.</li>
 *   <li>JWT {@code role} ∈ {SME_ADMIN, ACCOUNTANT, PLATFORM_ADMIN} via {@link JwtUtil#requireRole}.</li>
 *   <li>JWT {@code active_tenant_id} present and parseable as UUID via {@link JwtUtil#requireUuidClaim}.</li>
 *   <li>Per-tenant concurrent-batch gate (max 3) via {@link BatchPackagingConcurrencyGate}.</li>
 *   <li>Monthly-cap pre-check (best-effort, AC #9): rejects if remaining cap &lt; pairs.size().</li>
 * </ol>
 *
 * <p>Tenant id is taken EXCLUSIVELY from the JWT — never from the request body
 * (Story 9.4 retro lesson, AC #6).
 */
@RestController
@RequestMapping("/api/v1/classifier/batch-packaging")
@TierRequired(Tier.PRO_EPR)
public class BatchPackagingClassifierController {

    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");
    private static final long CONCURRENT_LIMIT_RETRY_AFTER_SECONDS = 5L;

    private final BatchPackagingClassifierService batchService;
    private final ClassifierUsageService usageService;
    private final BatchPackagingConcurrencyGate concurrencyGate;

    public BatchPackagingClassifierController(
            BatchPackagingClassifierService batchService,
            ClassifierUsageService usageService,
            BatchPackagingConcurrencyGate concurrencyGate) {
        this.batchService = batchService;
        this.usageService = usageService;
        this.concurrencyGate = concurrencyGate;
    }

    @PostMapping
    public BatchPackagingResponse classify(
            @Valid @RequestBody BatchPackagingRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        JwtUtil.requireRole(jwt,
                "batch classification requires SME_ADMIN, ACCOUNTANT, or PLATFORM_ADMIN",
                "SME_ADMIN", "ACCOUNTANT", "PLATFORM_ADMIN");
        UUID tenantId = JwtUtil.requireUuidClaim(jwt, "active_tenant_id");

        if (!concurrencyGate.tryAcquire(tenantId)) {
            throw new BatchConcurrencyLimitExceededException(concurrencyGate.permitsPerTenant());
        }
        try {
            int monthlyCap = usageService.getMonthlyCap();
            int used = usageService.getCurrentMonthCallCount(tenantId);
            int remaining = Math.max(0, monthlyCap - used);
            if (remaining < request.pairs().size()) {
                throw new ClassifierCapExceededException(
                        ClassifierUsageInfo.of(used, monthlyCap), request.pairs().size());
            }

            List<BatchPackagingResult> results = batchService.classify(request.pairs(), tenantId);

            int usedAfter = usageService.getCurrentMonthCallCount(tenantId);
            return BatchPackagingResponse.from(results, ClassifierUsageInfo.of(usedAfter, monthlyCap));
        } finally {
            concurrencyGate.release(tenantId);
        }
    }

    @ExceptionHandler(ClassifierCapExceededException.class)
    public ResponseEntity<ClassifierUsageInfo> handleCapExceeded(ClassifierCapExceededException ex) {
        // AC #8: body is ClassifierUsageInfo; the human-readable message
        // ("Monthly classifier cap would be exceeded: N pairs requested, M remaining.")
        // is surfaced via X-Error-Message so clients can log or display it without
        // breaking the declared body contract.
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(secondsUntilNextBudapestMonth()))
                .header("X-Error-Message", ex.getMessage())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.usageInfo());
    }

    @ExceptionHandler(BatchConcurrencyLimitExceededException.class)
    public ResponseEntity<String> handleConcurrencyLimit(BatchConcurrencyLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(CONCURRENT_LIMIT_RETRY_AFTER_SECONDS))
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.getMessage());
    }

    private static long secondsUntilNextBudapestMonth() {
        ZonedDateTime now = ZonedDateTime.now(BUDAPEST);
        // Navigate via YearMonth so "last day of the month + 1 month" never clamps the
        // day-of-month back into the current month (e.g. Jan 31 → Feb 28, withDayOfMonth(1)
        // → Feb 1 SAME year, which was a negative duration pre-fix).
        ZonedDateTime nextMonthStart = YearMonth.from(now).plusMonths(1).atDay(1).atStartOfDay(BUDAPEST);
        long seconds = Duration.between(now, nextMonthStart).getSeconds();
        return Math.max(1L, seconds);
    }
}
