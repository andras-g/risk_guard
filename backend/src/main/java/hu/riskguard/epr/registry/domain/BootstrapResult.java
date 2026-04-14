package hu.riskguard.epr.registry.domain;

/**
 * Summary returned by {@code RegistryBootstrapService.triggerBootstrap()}.
 *
 * @param created number of new candidates persisted
 * @param skipped number of invoice lines skipped because a candidate with the
 *                same dedup key already existed (in any status)
 */
public record BootstrapResult(int created, int skipped) {}
