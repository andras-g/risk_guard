package hu.riskguard.epr.registry.domain;

import hu.riskguard.epr.registry.internal.RegistryRepository;
import hu.riskguard.jooq.tables.records.ProductsRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lookup service for resolving invoice line items to registry products.
 *
 * <p>Lookup priority:
 * <ol>
 *   <li>If {@code articleNumber} is non-blank: exact article number match on ACTIVE products.</li>
 *   <li>Fallback: first ACTIVE product by exact VTSZ match, ordered by {@code updated_at ASC}.</li>
 *   <li>Returns {@link Optional#empty()} if no match found.</li>
 * </ol>
 *
 * <p><b>Critical:</b> every query is scoped to the provided {@code tenantId}.
 * NEVER pass {@code null} — missing tenant context throws {@link IllegalStateException}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistryLookupService {

    private final RegistryRepository registryRepository;

    /**
     * Find a registry product for a tenant by article number (preferred) or VTSZ.
     *
     * @param tenantId      the tenant whose registry to search — MUST NOT be null
     * @param vtsz          the VTSZ code from the invoice line
     * @param articleNumber the article number from the invoice line (may be null/blank)
     * @return a {@link RegistryMatch} if found, or empty
     * @throws IllegalStateException if tenantId is null (programming error)
     */
    public Optional<RegistryMatch> findByVtszOrArticleNumber(UUID tenantId, String vtsz, String articleNumber) {
        if (tenantId == null) {
            throw new IllegalStateException("CRITICAL: tenantId must not be null in RegistryLookupService");
        }

        // Step 1: article number match (if provided)
        if (articleNumber != null && !articleNumber.isBlank()) {
            Optional<ProductsRecord> articleMatch =
                    registryRepository.findActiveByArticleNumber(tenantId, articleNumber);
            if (articleMatch.isPresent()) {
                return buildMatch(tenantId, articleMatch.get());
            }
        }

        // Step 2: VTSZ match (first ACTIVE product, oldest updated_at on tie)
        if (vtsz != null && !vtsz.isBlank()) {
            List<ProductsRecord> vtszMatches = registryRepository.findActiveByVtsz(tenantId, vtsz);
            if (!vtszMatches.isEmpty()) {
                return buildMatch(tenantId, vtszMatches.get(0));
            }
        }

        return Optional.empty();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Optional<RegistryMatch> buildMatch(UUID tenantId, ProductsRecord product) {
        List<ProductPackagingComponent> components =
                registryRepository.findComponentsByProductAndTenant(product.getId(), tenantId)
                        .stream()
                        .map(RegistryRepository::toComponent)
                        .toList();
        return Optional.of(new RegistryMatch(product.getId(), components));
    }
}
