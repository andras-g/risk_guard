package hu.riskguard.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit rules enforcing the architectural invariants introduced by Sprint Change Proposal CP-5
 * (Epic 9 — Product-Packaging Registry).
 *
 * Each rule uses {@code allowEmptyShould(true)} because the target classes (registry sub-module,
 * OkirkapuXmlExporter, RecyclabilityGrade enum) do not yet exist on the main branch — they ship
 * incrementally with Stories 9.1 and 9.4. The rules become binding the moment those classes land,
 * with no further test-file edits required.
 *
 * Source of truth for the invariants:
 *   _bmad-output/planning-artifacts/sprint-change-proposal-2026-04-14.md §5
 *   docs/architecture/adrs/ADR-0002-pluggable-epr-report-target.md
 */
@AnalyzeClasses(packages = "hu.riskguard")
public class EpicNineInvariantsTest {

    /**
     * CP-5 §5 invariant 1 — registry write boundary.
     *
     * Only classes inside the registry sub-module may reach the {@code product_packaging_components}
     * jOOQ table. Other modules read aggregated results through {@code RegistryService}, never the
     * table directly.
     *
     * Targets the jOOQ-generated table class and its record by fully qualified name (string match)
     * because the table is created by Story 9.1's Flyway migration and codegen, and does not exist
     * on the main branch yet.
     */
    @ArchTest
    static final ArchRule only_registry_package_writes_to_product_packaging_components =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.registry..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().resideOutsideOfPackage("..jooq.tables..")   // jOOQ-generated table + record classes reference each other by design
                    .and().resideOutsideOfPackage("hu.riskguard.jooq") // jOOQ-generated infra (Keys, Tables, Indexes, Public) lives in the root jooq package
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .and().haveSimpleNameNotContaining("BeanDefinitions")
                    .and().haveSimpleNameNotContaining("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().haveFullyQualifiedName(
                            "hu.riskguard.jooq.tables.ProductPackagingComponents")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName(
                            "hu.riskguard.jooq.tables.records.ProductPackagingComponentsRecord");

    /**
     * CP-5 §5 invariant 3 — pluggable EPR report target.
     *
     * No class outside {@code hu.riskguard.epr.report..} may depend on the concrete
     * {@code OkirkapuXmlExporter} (or any future sibling implementation). Callers depend on the
     * {@code EprReportTarget} interface only.
     *
     * Today's only implementation is {@code OkirkapuXmlExporter}. When {@code EuRegistryAdapter}
     * ships post-2029, add it to the orShould chain — or generalise the rule to forbid dependency
     * on any class in {@code ..epr.report.internal..} from outside the report package.
     *
     * Pre-emptive note: a class named {@code MohuExporter} currently exists in
     * {@code hu.riskguard.epr.domain} (pre-CP-5 naming). Story 9.4 either renames or replaces it.
     * Until then, this rule does NOT forbid {@code MohuExporter} dependencies — the rule activates
     * for OkirkapuXmlExporter specifically. See ADR-0002 for the rationale.
     */
    @ArchTest
    static final ArchRule only_report_package_depends_on_concrete_report_target =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.report..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .and().haveSimpleNameNotContaining("BeanDefinitions")
                    .and().haveSimpleNameNotContaining("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().haveSimpleName("OkirkapuXmlExporter")
                    .allowEmptyShould(true);

    /**
     * CP-5 §5 invariant 3b — aggregator and marshaller are package-private internals.
     *
     * No class outside {@code hu.riskguard.epr.report..} may depend on the concrete
     * {@code KgKgyfNeAggregator} or {@code KgKgyfNeMarshaller} implementation classes.
     * They are package-private to enforce this at language level, but this ArchUnit rule
     * provides a belt-and-suspenders compile-time guard.
     *
     * Pre-emptive: {@code allowEmptyShould(true)} until these classes exist on main.
     */
    @ArchTest
    static final ArchRule aggregator_and_marshaller_not_accessed_from_outside_report_package =
            noClasses()
                    .that().resideOutsideOfPackage("..epr.report..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .and().haveSimpleNameNotContaining("BeanDefinitions")
                    .and().haveSimpleNameNotContaining("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().haveSimpleName("KgKgyfNeAggregator")
                    .orShould().dependOnClassesThat().haveSimpleName("KgKgyfNeMarshaller")
                    .allowEmptyShould(true);

    /**
     * CP-5 §5 invariant 4 — fee modulation rules are data, not code.
     *
     * Future PPWR eco-modulation (mandatory 2030) will key fees off recyclability grade
     * (A/B/C/D). The intent: any fee-rate lookup must come from the EPR config / DB, not from
     * a hard-coded if/switch on a {@code RecyclabilityGrade} enum value.
     *
     * Pure ArchUnit cannot precisely detect "switch on enum constants used in arithmetic". This
     * rule applies a coarser proxy: classes in fee-calculation areas of the EPR module
     * ({@code epr.domain}, {@code epr.report}) may not reference a {@code RecyclabilityGrade}
     * type at all. Storage of the value (in the registry sub-module, or in jOOQ records) is
     * unaffected.
     *
     * The {@code RecyclabilityGrade} enum does not exist yet. The rule is vacuous until it lands
     * (likely Story 9.4-era when fee-modulation work begins). At that point this rule binds
     * automatically; no test edit needed. Code review still catches inventive workarounds.
     */
    @ArchTest
    static final ArchRule fee_calculation_must_not_branch_on_recyclability_grade =
            noClasses()
                    .that().resideInAnyPackage("..epr.domain..", "..epr.report..")
                    .and().resideOutsideOfPackage("..registry..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .should().dependOnClassesThat().haveSimpleName("RecyclabilityGrade")
                    .allowEmptyShould(true);
}
