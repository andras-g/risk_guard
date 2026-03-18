/**
 * Test infrastructure module — provides authentication bypass and test data seeding
 * endpoints that are ONLY active under the {@code test} or {@code e2e} Spring profiles.
 *
 * <p>This module is excluded from ArchUnit naming convention checks because
 * test endpoints intentionally use {@code /api/test/} path prefix instead of
 * the standard {@code /api/v1/} versioned pattern.
 *
 * <p><strong>Security:</strong> All beans in this package are annotated with
 * {@code @Profile({"test", "e2e"})} and will not be instantiated in production.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"core", "identity::domain"}
)
package hu.riskguard.testing;
