package hu.riskguard.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * ArchUnit rules enforcing the architectural invariants introduced by Epic 10
 * (Invoice-driven Product-Centric EPR Filing).
 *
 * <p>Source of truth: {@code docs/architecture/adrs/ADR-0003-epic-10-audit-architecture.md}.
 *
 * <p>Each rule uses {@code allowEmptyShould(true)} so the rule binds the moment the
 * referenced classes land on main (same pattern as {@link EpicNineInvariantsTest}).
 *
 * <p>Tests are excluded via {@link ImportOption.DoNotIncludeTests} rather than a
 * name-based {@code haveSimpleNameNotEndingWith("Test")} filter — the name filter
 * silently lets fixture/seeder classes bypass the rule.
 */
@AnalyzeClasses(packages = "hu.riskguard", importOptions = ImportOption.DoNotIncludeTests.class)
public class EpicTenInvariantsTest {

    /**
     * ADR-0003 invariant 1 — audit write boundary.
     *
     * Only classes inside {@code hu.riskguard.epr.audit} may depend on the audit
     * repositories. Everything else routes through {@code AuditService}.
     *
     * <p>Target repositories are matched by package ({@code ..epr.audit.internal..}) rather
     * than simple name — {@code haveSimpleName("RegistryAuditRepository")} is fragile to
     * homonyms and silently defeated by renames.
     *
     * <p>Spring AOT code-gen classes (e.g., {@code Foo__TestContext001_BeanDefinitions},
     * {@code Bar__TestContext001_BeanFactoryRegistrations}) are excluded by suffix rather
     * than substring so a production class containing the substring {@code "BeanDefinitions"}
     * cannot sneak past the rule.
     */
    @ArchTest
    static final ArchRule only_audit_package_writes_to_audit_tables =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.audit..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().resideOutsideOfPackage("..jooq..")
                    .and().haveSimpleNameNotEndingWith("BeanDefinitions")
                    .and().haveSimpleNameNotEndingWith("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().resideInAPackage("..epr.audit.internal..")
                    .allowEmptyShould(true);

    /**
     * ADR-0003 invariant 2 — {@code AuditService} is the module facade.
     *
     * The class must reside in {@code hu.riskguard.epr.audit..} (subpackages allowed, e.g.,
     * a future {@code epr.audit.facade} move remains valid) and be annotated {@link Service}.
     * It MUST NOT be annotated {@link Transactional}; audit writes inherit the caller's
     * transaction so they commit atomically with the mutation that prompted them. Marking
     * the facade {@code @Transactional} re-couples with the forbidden pattern removed in
     * the Story 10.1 tx-pool refactor. See ADR-0003 §"Hard rule".
     *
     * <p>Combined with invariant 1, this guarantees a single Spring-managed entry point
     * for all audit writes across Stories 10.1–10.9.
     */
    @ArchTest
    static final ArchRule audit_service_is_the_facade =
            classes()
                    .that().haveSimpleName("AuditService")
                    .should().resideInAPackage("..epr.audit..")
                    .andShould().beAnnotatedWith(Service.class)
                    .andShould().notBeAnnotatedWith(Transactional.class)
                    .allowEmptyShould(true);

    /**
     * Story 10.4 AC #31 invariant 3 — bootstrap classes reside in the bootstrap package.
     *
     * <p>Classes named *BootstrapService, *BootstrapWorker, or *BootstrapController that
     * belong to the {@code epr.registry.bootstrap} or its sub-packages must NOT appear
     * elsewhere. This prevents accidental re-introduction of Story 9.2's top-level
     * bootstrap service in the wrong package.
     */
    @ArchTest
    static final ArchRule bootstrap_service_lives_in_bootstrap_package =
            classes()
                    .that().haveSimpleNameEndingWith("BootstrapService")
                    .or().haveSimpleNameEndingWith("BootstrapWorker")
                    .or().haveSimpleNameEndingWith("BootstrapController")
                    .should().resideInAPackage("..epr.registry.bootstrap..")
                    .allowEmptyShould(true);

    /**
     * Story 10.4 AC #31 invariant 4 — only the bootstrap package may reference EPR_BOOTSTRAP_JOBS.
     *
     * <p>No class outside {@code ..epr.registry.bootstrap..} may import or depend on
     * the jOOQ-generated {@code EprBootstrapJobs} table class (or its record). This mirrors
     * the audit-boundary invariant and ensures the new table has a single Java owner.
     */
    @ArchTest
    static final ArchRule only_bootstrap_package_writes_to_epr_bootstrap_jobs =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.registry.bootstrap..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().resideOutsideOfPackage("..jooq..")
                    .and().haveSimpleNameNotEndingWith("BeanDefinitions")
                    .and().haveSimpleNameNotEndingWith("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().haveSimpleName("EprBootstrapJobs")
                    .allowEmptyShould(true);

    // ── AC #35: witness pair for AC #31 ArchUnit rules ─────────────────────────
    //
    // The four @ArchTest rules above bind only when `allowEmptyShould(true)` permits an
    // empty match set — so a silently passing build (no bootstrap classes yet) isn't a
    // signal the rule works. To prove the rule ACTIVELY fires, un-comment one of the
    // witnesses below and run `./gradlew test --tests "*EpicTenInvariants*"`. The build
    // MUST fail with an ArchUnit violation. Re-comment before pushing.
    //
    // NOTE: The witnesses live in comments rather than as disabled tests so the source
    // stays syntactically valid without introducing a second "violating" package for
    // ArchUnit to accidentally scan when the tests are re-enabled in a different form.
    //
    // Witness for bootstrap_service_lives_in_bootstrap_package:
    //   1. Create src/main/java/hu/riskguard/epr/registry/domain/RoqueBootstrapService.java
    //      with any @Service. Expected failure message:
    //        "Class … RoqueBootstrapService was expected to reside in package
    //         '..epr.registry.bootstrap..'."
    //
    // Witness for only_bootstrap_package_writes_to_epr_bootstrap_jobs:
    //   1. In any class outside hu.riskguard.epr.registry.bootstrap (e.g., EprService),
    //      add:   import static hu.riskguard.jooq.Tables.EPR_BOOTSTRAP_JOBS;
    //      and reference EPR_BOOTSTRAP_JOBS anywhere. Expected failure message:
    //        "Class … depends on class … EprBootstrapJobs."
    //
    // Witness for audit_service_is_the_facade:
    //   1. Annotate AuditService with @Transactional. Expected failure message:
    //        "Class … AuditService is annotated with @Transactional."
    //
    // Witness for only_audit_package_writes_to_audit_tables:
    //   1. In any class outside ..epr.audit.., add a dependency on
    //      hu.riskguard.epr.audit.internal.RegistryAuditRepository. Expected failure:
    //        "Class … depends on class … RegistryAuditRepository."
}
