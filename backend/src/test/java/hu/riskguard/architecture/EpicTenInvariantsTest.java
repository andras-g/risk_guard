package hu.riskguard.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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
     * Story 10.8 AC #16 — aggregation audit write boundary.
     *
     * <p>Only classes inside {@code hu.riskguard.epr.audit} may depend on
     * {@code AggregationAuditRepository}. Mirrors invariant 1 for the new table.
     */
    @ArchTest
    static final ArchRule only_audit_package_writes_to_aggregation_audit_log =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.audit..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().resideOutsideOfPackage("..jooq..")
                    .and().haveSimpleNameNotEndingWith("BeanDefinitions")
                    .and().haveSimpleNameNotEndingWith("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().haveSimpleName("AggregationAuditRepository")
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

    /**
     * Story 10.11 AC #13 — scope writes go through the audit-aware service layer.
     *
     * <p>Only {@code RegistryService} (the audit-enforced scope-mutation facade) may call
     * {@code RegistryRepository.updateEprScope(...)}. Any other caller bypasses the audit trail
     * and the aggregator-cache invalidation step — a silent compliance regression. The bootstrap
     * service inserts a fresh row with {@code PRODUCTS.EPR_SCOPE} set directly (AC #11); that is
     * an INSERT, not an UPDATE, so it does not hit {@code updateEprScope}.
     *
     * <p>Allowed packages: {@code ..epr.registry.domain..} ({@code RegistryService} itself),
     * {@code ..epr.registry.internal..} ({@code RegistryRepository}), and {@code ..epr.audit..}
     * (the facade's own machinery). The bootstrap service in
     * {@code ..epr.registry.bootstrap.domain..} INSERTs but never UPDATEs scope, so it does not
     * need to appear here for the method-call rule (it is allowed by the field-access rule below).
     */
    @ArchTest
    static final ArchRule only_audit_package_writes_to_products_epr_scope =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.registry.domain..")
                    .and().resideOutsideOfPackage("..epr.registry.internal..")
                    .and().resideOutsideOfPackage("..epr.audit..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().resideOutsideOfPackage("..jooq..")
                    .and().haveSimpleNameNotEndingWith("BeanDefinitions")
                    .and().haveSimpleNameNotEndingWith("BeanFactoryRegistrations")
                    .should().callMethodWhere(callToRegistryRepositoryUpdateEprScope())
                    .allowEmptyShould(true);

    private static DescribedPredicate<JavaMethodCall> callToRegistryRepositoryUpdateEprScope() {
        return new DescribedPredicate<>("call RegistryRepository.updateEprScope(...)") {
            @Override
            public boolean test(JavaMethodCall call) {
                return "updateEprScope".equals(call.getTarget().getName())
                        && call.getTargetOwner().getFullName().endsWith("RegistryRepository");
            }
        };
    }

    /**
     * Story 10.11 AC #13 (R2 reinforcement) — no class outside the allowed packages may even
     * REFERENCE the {@code Products.EPR_SCOPE} jOOQ field. Closes the gap left by the method-call
     * rule above: a one-off {@code dsl.update(PRODUCTS).set(PRODUCTS.EPR_SCOPE, ...)} (or an
     * INSERT with {@code .set(PRODUCTS.EPR_SCOPE, ...)}) anywhere in the codebase would pass the
     * call-site rule but bypass the audit / cache-invalidation invariant. This rule catches the
     * {@code getstatic} bytecode that any direct field reference emits.
     *
     * <p>Allowed packages:
     * <ul>
     *   <li>{@code ..epr.audit..} — audit facade machinery may read scope to compute diffs.</li>
     *   <li>{@code ..epr.registry.internal..} — {@code RegistryRepository} (the only writer).</li>
     *   <li>{@code ..epr.registry.bootstrap.domain..} — {@code InvoiceDrivenRegistryBootstrapService}
     *       INSERTs new products with the tenant default scope (AC #11). Note: spec text named the
     *       bootstrap package {@code ..bootstrap.internal..} but the actual class lives in
     *       {@code ..bootstrap.domain..} (verified on disk, R2 review correction).</li>
     *   <li>{@code ..architecture..} and {@code ..jooq..} — this test + generated code.</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule no_direct_references_to_products_epr_scope_field =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.audit..")
                    .and().resideOutsideOfPackage("..epr.registry.internal..")
                    .and().resideOutsideOfPackage("..epr.registry.bootstrap.domain..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().resideOutsideOfPackage("..jooq..")
                    .and().haveSimpleNameNotEndingWith("BeanDefinitions")
                    .and().haveSimpleNameNotEndingWith("BeanFactoryRegistrations")
                    .should().accessFieldWhere(referencesProductsEprScopeField())
                    .allowEmptyShould(true);

    private static DescribedPredicate<JavaFieldAccess> referencesProductsEprScopeField() {
        return new DescribedPredicate<>("reference Products.EPR_SCOPE jOOQ field") {
            @Override
            public boolean test(JavaFieldAccess access) {
                return "EPR_SCOPE".equals(access.getTarget().getName())
                        && access.getTarget().getOwner().getFullName().endsWith(".Products");
            }
        };
    }

    /**
     * Story 10.5 T3 invariant 5 — no double/float fields in the aggregation package.
     *
     * <p>IEEE-754 double/float arithmetic causes rounding drift in EPR weight totals.
     * All aggregation arithmetic must use {@link BigDecimal} with {@code MathContext.DECIMAL64}.
     */
    @ArchTest
    static final ArchRule no_double_or_float_fields_in_aggregation_package =
            noFields()
                    .that().areDeclaredInClassesThat().resideInAPackage("..epr.aggregation..")
                    .should().haveRawType(double.class)
                    .orShould().haveRawType(float.class)
                    .orShould().haveRawType(Double.class)
                    .orShould().haveRawType(Float.class)
                    .allowEmptyShould(true);

    /**
     * Story 10.5 T3 invariant 5b — no double/float method return types in the aggregation package.
     */
    @ArchTest
    static final ArchRule no_double_or_float_return_types_in_aggregation_package =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..epr.aggregation..")
                    .should().haveRawReturnType(double.class)
                    .orShould().haveRawReturnType(float.class)
                    .orShould().haveRawReturnType(Double.class)
                    .orShould().haveRawReturnType(Float.class)
                    .allowEmptyShould(true);

    /**
     * Story 10.5 T3 invariant 5c — no double/float parameter types in the aggregation package.
     *
     * <p>Covers method and constructor parameters (local variables are erased at bytecode level —
     * catching those via ArchUnit would require source parsing, not supported by ArchUnit).
     */
    @ArchTest
    static final ArchRule no_double_or_float_parameters_in_aggregation_package =
            noCodeUnits()
                    .that().areDeclaredInClassesThat().resideInAPackage("..epr.aggregation..")
                    .should(haveDoubleOrFloatParameter())
                    .allowEmptyShould(true);

    /**
     * Story 10.5 T3 invariant 6 — no double-backed {@link BigDecimal} construction in the
     * aggregation package.
     *
     * <p>Both {@code new BigDecimal(double)} AND {@code BigDecimal.valueOf(double)} are banned:
     * both widen an IEEE-754 double to a BigDecimal, which defeats the decimal-literal contract
     * (e.g. {@code BigDecimal.valueOf(0.1).toPlainString() == "0.1"} today but relies on
     * {@link Double#toString(double)} — round-trip stability is not guaranteed for all decimals).
     * Construct BigDecimals from {@code String} or integer literals only.
     */
    @ArchTest
    static final ArchRule no_bigdecimal_from_double_in_aggregation_package =
            noClasses()
                    .that().resideInAPackage("..epr.aggregation..")
                    .should().callConstructor(BigDecimal.class, double.class)
                    .orShould().callMethod(BigDecimal.class, "valueOf", double.class)
                    .allowEmptyShould(true);

    // ── Helper: custom condition for parameter-type checks ──────────────────────

    private static final Set<String> DOUBLE_FLOAT_TYPES = Set.of(
            "double", "float", "java.lang.Double", "java.lang.Float");

    private static ArchCondition<JavaCodeUnit> haveDoubleOrFloatParameter() {
        return new ArchCondition<>("have a parameter of raw type double, float, Double, or Float") {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                for (JavaClass param : codeUnit.getRawParameterTypes()) {
                    if (DOUBLE_FLOAT_TYPES.contains(param.getName())) {
                        events.add(SimpleConditionEvent.satisfied(codeUnit,
                                codeUnit.getFullName() + " has parameter of raw type " + param.getName()));
                        return;
                    }
                }
            }
        };
    }

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
    //
    // Witness for no_double_or_float_fields_in_aggregation_package (Story 10.5 T3):
    //   1. Add a field `private double badField = 0.0;` to any class in
    //      hu.riskguard.epr.aggregation.*. Expected failure message:
    //        "Field … has raw type double."
    //
    // Witness for no_double_or_float_return_types_in_aggregation_package (Story 10.5 T3):
    //   1. Add a method `double weight() { return 0.0; }` to any class in
    //      hu.riskguard.epr.aggregation.*. Expected failure message:
    //        "Method … has raw return type double."
    //
    // Witness for no_double_or_float_parameters_in_aggregation_package (Story 10.5 T3):
    //   1. Add a method `void set(double x) {}` to any class in
    //      hu.riskguard.epr.aggregation.*. Expected failure message:
    //        "Method … has parameter of raw type double."
    //
    // Witness for no_bigdecimal_from_double_in_aggregation_package (Story 10.5 T3):
    //   1. Add `new BigDecimal(0.1)` OR `BigDecimal.valueOf(0.1d)` anywhere in a class in
    //      hu.riskguard.epr.aggregation.*. Expected failure message:
    //        "Constructor <java.math.BigDecimal.<init>(double)> gets called …"  OR
    //        "Method <java.math.BigDecimal.valueOf(double)> gets called …"
    //      Construct BigDecimals from String or integer literals only; DO NOT substitute
    //      BigDecimal.valueOf(double) — it is banned by the same rule.

    // ── Story 10.11 AC #27 — explicit witness @Test methods ────────────────────
    //
    // Unlike the other witnesses (which live as comments because they would require a
    // temporary main-source violation to assert), the Story 10.11 scope-write rule has a
    // dedicated test-only fixture ({@link hu.riskguard.archtestfixture.scope.ScopeRuleRogueWriter})
    // that calls {@code RegistryRepository.updateEprScope} from a package outside the rule's
    // allowed-list. Two JUnit @Test methods below use ArchUnit's programmatic API to:
    //   1. Positive witness: scan production classes only (tests excluded) and assert the rule
    //      passes — proves the rule is active and legitimate writers are not false-positives.
    //   2. Negative witness: scan production + the test fixture and assert the rule FAILS with
    //      a violation naming {@link hu.riskguard.archtestfixture.scope.ScopeRuleRogueWriter}.
    //
    // Keeping both witnesses as real tests (rather than comments) is a review T12 follow-up
    // from Story 10.11 (P12) — AC #27 requires explicit @Test methods, not commented hints.

    @Test
    void scopeRule_positive_witness_passes_on_production_classes() {
        JavaClasses productionOnly = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("hu.riskguard");
        assertDoesNotThrow(
                () -> only_audit_package_writes_to_products_epr_scope.check(productionOnly),
                "only_audit_package_writes_to_products_epr_scope must pass for production classes — "
                        + "a failure here means some legitimate writer moved out of an allowed package");
    }

    @Test
    void scopeRule_negative_witness_fires_on_rogue_test_fixture() {
        JavaClasses withFixture = new ClassFileImporter()
                .importPackages(
                        "hu.riskguard.epr.registry.internal",
                        "hu.riskguard.archtestfixture.scope");
        AssertionError violation = assertThrows(AssertionError.class,
                () -> only_audit_package_writes_to_products_epr_scope.check(withFixture),
                "only_audit_package_writes_to_products_epr_scope must fail when a class outside "
                        + "the allowed-list calls RegistryRepository.updateEprScope(...)");
        assertTrue(violation.getMessage().contains("ScopeRuleRogueWriter"),
                "ArchUnit violation message should name the offending class (ScopeRuleRogueWriter). "
                        + "Actual message: " + violation.getMessage());
    }
}
