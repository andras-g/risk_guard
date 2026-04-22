package hu.riskguard.archtestfixture.scope;

import hu.riskguard.epr.registry.internal.RegistryRepository;

import java.util.UUID;

/**
 * Deliberate architectural violation used as the negative witness for
 * {@code only_audit_package_writes_to_products_epr_scope} in
 * {@link hu.riskguard.architecture.EpicTenInvariantsTest}.
 *
 * <p>This class resides in a package that is <em>not</em> in the allowed-list of the rule
 * ({@code ..epr.registry.domain..}, {@code ..epr.registry.internal..}, {@code ..epr.audit..},
 * {@code ..architecture..}, {@code ..jooq..}), so calling
 * {@link RegistryRepository#updateEprScope(UUID, UUID, String)} here MUST be flagged by the rule.
 *
 * <p>It is excluded from the {@code @AnalyzeClasses} scan on {@code EpicTenInvariantsTest}
 * because that scan uses {@code ImportOption.DoNotIncludeTests} — this file only appears in
 * the class path when a targeted programmatic {@code ClassFileImporter} opts into test classes.
 */
public class ScopeRuleRogueWriter {

    private final RegistryRepository repository;

    public ScopeRuleRogueWriter(RegistryRepository repository) {
        this.repository = repository;
    }

    public void doRogueUpdate(UUID productId, UUID tenantId) {
        repository.updateEprScope(productId, tenantId, "RESELLER");
    }
}
