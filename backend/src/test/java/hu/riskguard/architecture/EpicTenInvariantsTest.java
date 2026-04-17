package hu.riskguard.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
