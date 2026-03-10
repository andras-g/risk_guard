package hu.riskguard.screening.domain;

import hu.riskguard.screening.api.dto.VerdictResponse;
import hu.riskguard.screening.domain.events.PartnerSearchCompleted;
import hu.riskguard.screening.internal.ScreeningRepository;
import hu.riskguard.screening.internal.ScreeningRepository.FreshSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain service facade for screening operations.
 * This is the ONLY public entry point into the screening module's business logic.
 *
 * <p>Follows the module facade pattern: Controller → ScreeningService → ScreeningRepository.
 * External modules must use this facade (or application events) — never the repository directly.
 */
@Service
@RequiredArgsConstructor
public class ScreeningService {

    private final ScreeningRepository screeningRepository;
    private final ApplicationEventPublisher eventPublisher;

    // TODO: Move to risk-guard-tokens.json when the token registry is expanded for screening constants
    private static final int FRESHNESS_THRESHOLD_MINUTES = 15;

    private static final String DISCLAIMER_TEXT =
            "This search result is provided for informational purposes only. " +
            "Data is sourced from Hungarian government registries and may not reflect real-time status.";

    /**
     * Execute a partner search for the given tax number.
     *
     * <p>Flow:
     * <ol>
     *   <li>Normalize the tax number (strip hyphens/whitespace)</li>
     *   <li>Check idempotency guard — return cached verdict if fresh snapshot exists (< 15 min)</li>
     *   <li>Create stub CompanySnapshot with empty snapshot_data JSONB</li>
     *   <li>Create Verdict with status INCOMPLETE (no scrapers yet)</li>
     *   <li>Write audit log entry with SHA-256 hash</li>
     *   <li>Publish PartnerSearchCompleted event</li>
     *   <li>Return VerdictResponse</li>
     * </ol>
     *
     * @param taxNumber the Hungarian tax number (8 or 11 digits, may contain hyphens)
     * @param userId    the user performing the search (from JWT)
     * @return VerdictResponse with the search result
     */
    @Transactional
    public VerdictResponse search(String taxNumber, UUID userId, UUID tenantId) {
        String normalizedTaxNumber = taxNumber.replaceAll("[\\s-]", "");

        // Idempotency guard: return cached result if fresh snapshot exists
        Optional<FreshSnapshot> fresh = screeningRepository.findFreshSnapshot(
                normalizedTaxNumber, FRESHNESS_THRESHOLD_MINUTES);

        if (fresh.isPresent()) {
            FreshSnapshot cached = fresh.get();
            return VerdictResponse.from(
                    cached.verdictId(),
                    cached.snapshotId(),
                    normalizedTaxNumber,
                    cached.status().getLiteral(),
                    cached.confidence().getLiteral(),
                    cached.createdAt()
            );
        }

        // Create new snapshot and verdict
        UUID snapshotId = screeningRepository.createSnapshot(normalizedTaxNumber);
        UUID verdictId = screeningRepository.createVerdict(snapshotId);

        // Write audit log
        screeningRepository.writeAuditLog(normalizedTaxNumber, userId, DISCLAIMER_TEXT);

        // Publish event for downstream consumers
        eventPublisher.publishEvent(PartnerSearchCompleted.of(snapshotId, verdictId, tenantId));

        return VerdictResponse.from(
                verdictId,
                snapshotId,
                normalizedTaxNumber,
                "INCOMPLETE",
                "UNAVAILABLE",
                java.time.OffsetDateTime.now()
        );
    }
}
