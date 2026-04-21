package hu.riskguard.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "hu.riskguard")
public class NamingConventionTest {

    @ArchTest
    static final ArchRule services_should_be_suffixed_with_service =
            classes().that().areAnnotatedWith(Service.class)
                    .should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule controllers_should_be_suffixed_with_controller =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule api_paths_should_match_pattern =
            classes().that().areAnnotatedWith(RestController.class)
                    .and().resideOutsideOfPackage("..testing..")
                    .should(new ArchCondition<JavaClass>("have RequestMapping matching /api/v[0-9]+/[a-z-]+") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            if (!javaClass.isAnnotatedWith(RequestMapping.class)) {
                                events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " is not annotated with @RequestMapping"));
                                return;
                            }
                            RequestMapping mapping = javaClass.getAnnotationOfType(RequestMapping.class);
                            String[] paths = mapping.value();
                            if (paths.length == 0) {
                                events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " has empty @RequestMapping"));
                            }
                            for (String path : paths) {
                                if (!path.matches("/api/v[0-9]+/[a-z-]+(/[a-z-]+)*") && !path.matches("/api/public/[a-z-]+(/[a-z-]+)*") && !path.matches("/api/v[0-9]+/public/[a-z-]+(/[a-z-]+)*")) {
                                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " has invalid path " + path));
                                }
                            }
                        }
                    });

    @ArchTest
    static final ArchRule dtos_should_be_records_or_enums =
            classes().that().resideInAPackage("..api.dto..")
                    .and().haveSimpleNameNotContaining("package-info")
                    .and().doNotHaveSimpleName("package-info")
                    .and().areNotEnums()
                    .should().beRecords();

    // --- Module boundary enforcement: jOOQ table isolation ---
    // Screening module may only access its own tables (company_snapshots, verdicts, search_audit_log).
    // This enforces the architecture's physical isolation boundary without per-module jOOQ codegen.
    // Note: Test classes are excluded — integration tests need to seed data across module boundaries.
    @ArchTest
    static final ArchRule screening_module_should_only_access_own_tables =
            classes().that().resideInAPackage("..screening..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .should(new ArchCondition<JavaClass>("only access screening-owned jOOQ tables (CompanySnapshots, Verdicts, SearchAuditLog)") {
                        private static final java.util.Set<String> ALLOWED_TABLES = java.util.Set.of(
                                "hu.riskguard.jooq.tables.CompanySnapshots",
                                "hu.riskguard.jooq.tables.Verdicts",
                                "hu.riskguard.jooq.tables.SearchAuditLog"
                        );
                        private static final String JOOQ_TABLES_PKG = "hu.riskguard.jooq.tables.";

                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            javaClass.getDirectDependenciesFromSelf().forEach(dep -> {
                                String targetName = dep.getTargetClass().getFullName();
                                if (targetName.startsWith(JOOQ_TABLES_PKG) && !ALLOWED_TABLES.contains(targetName)) {
                                    events.add(SimpleConditionEvent.violated(dep,
                                            javaClass.getName() + " accesses non-owned jOOQ table " + targetName));
                                }
                            });
                        }
                    });

    // Identity module may only access its own tables (tenants, users, tenant_mandates, guest_sessions).
    // Allows both table references and record types from owned tables.
    @ArchTest
    static final ArchRule identity_module_should_only_access_own_tables =
            classes().that().resideInAPackage("..identity..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .should(new ArchCondition<JavaClass>("only access identity-owned jOOQ tables") {
                        private static final java.util.Set<String> ALLOWED_TABLE_PREFIXES = java.util.Set.of(
                                "hu.riskguard.jooq.tables.Tenants",
                                "hu.riskguard.jooq.tables.Users",
                                "hu.riskguard.jooq.tables.TenantMandates",
                                "hu.riskguard.jooq.tables.GuestSessions",
                                "hu.riskguard.jooq.tables.RefreshTokens"
                        );
                        private static final String JOOQ_TABLES_PKG = "hu.riskguard.jooq.tables.";

                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            javaClass.getDirectDependenciesFromSelf().forEach(dep -> {
                                String targetName = dep.getTargetClass().getFullName();
                                if (targetName.startsWith(JOOQ_TABLES_PKG)) {
                                    boolean allowed = ALLOWED_TABLE_PREFIXES.stream()
                                            .anyMatch(prefix -> targetName.equals(prefix)
                                                    || targetName.startsWith(prefix + ".") // nested classes
                                                    || targetName.equals("hu.riskguard.jooq.tables.records." + extractRecordName(prefix)));
                                    if (!allowed) {
                                        events.add(SimpleConditionEvent.violated(dep,
                                                javaClass.getName() + " accesses non-owned jOOQ table " + targetName));
                                    }
                                }
                            });
                        }

                        private String extractRecordName(String tableClassName) {
                            // hu.riskguard.jooq.tables.Tenants → TenantsRecord
                            String simple = tableClassName.substring(tableClassName.lastIndexOf('.') + 1);
                            return simple + "Record";
                        }
                    });

    // Data source module may only access its own tables (AdapterHealth, CanaryCompanies, NavCredentials).
    // Note: datasource module does NOT directly access company_snapshots — that goes through ScreeningService facade.
    @ArchTest
    static final ArchRule datasource_module_should_only_access_own_tables =
            classes().that().resideInAPackage("..datasource..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .should(new ArchCondition<JavaClass>("only access datasource-owned jOOQ tables (AdapterHealth, CanaryCompanies, NavCredentials)") {
                        private static final java.util.Set<String> ALLOWED_TABLES = java.util.Set.of(
                                "hu.riskguard.jooq.tables.AdapterHealth",
                                "hu.riskguard.jooq.tables.CanaryCompanies",
                                "hu.riskguard.jooq.tables.NavCredentials"
                        );
                        private static final String JOOQ_TABLES_PKG = "hu.riskguard.jooq.tables.";

                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            javaClass.getDirectDependenciesFromSelf().forEach(dep -> {
                                String targetName = dep.getTargetClass().getFullName();
                                if (targetName.startsWith(JOOQ_TABLES_PKG) && !ALLOWED_TABLES.contains(targetName)) {
                                    events.add(SimpleConditionEvent.violated(dep,
                                            javaClass.getName() + " accesses non-owned jOOQ table " + targetName));
                                }
                            });
                        }
                    });

    // No external module should access datasource module's internal package.
    // Exceptions: domain facade within the same module, ArchUnit tests, and Spring AOT-generated classes.
    @ArchTest
    static final ArchRule datasource_internal_should_not_be_accessed_externally =
            noClasses().that().resideOutsideOfPackage("..datasource..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().haveSimpleNameNotContaining("BeanDefinitions")
                    .and().haveSimpleNameNotContaining("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().resideInAPackage("..datasource.internal..");

    // No external module should access screening module's internal package.
    // Exceptions: domain facade within the same module, ArchUnit tests, and Spring AOT-generated classes.
    @ArchTest
    static final ArchRule screening_internal_should_not_be_accessed_externally =
            noClasses().that().resideOutsideOfPackage("..screening..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().haveSimpleNameNotContaining("BeanDefinitions")
                    .and().haveSimpleNameNotContaining("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().resideInAPackage("..screening.internal..");

    // EPR module may only access its own tables (EprConfigs, EprCalculations, EprExports, EprMaterialTemplates).
    // Uses prefix-matching (identity pattern) to correctly handle jOOQ nested classes and record types.
    @ArchTest
    static final ArchRule epr_module_should_only_access_own_tables =
            classes().that().resideInAPackage("..epr..")
                    .and().haveSimpleNameNotEndingWith("Test")
                    .and().haveSimpleNameNotEndingWith("Tests")
                    .should(new ArchCondition<JavaClass>("only access epr-owned jOOQ tables") {
                        private static final java.util.Set<String> ALLOWED_TABLE_PREFIXES = java.util.Set.of(
                                "hu.riskguard.jooq.tables.EprConfigs",
                                "hu.riskguard.jooq.tables.EprCalculations",
                                "hu.riskguard.jooq.tables.EprExports",
                                "hu.riskguard.jooq.tables.EprMaterialTemplates",
                                // Story 9.1 — registry sub-module tables (no epr_ prefix per CP-5 §4.2)
                                "hu.riskguard.jooq.tables.Products",
                                "hu.riskguard.jooq.tables.ProductPackagingComponents",
                                "hu.riskguard.jooq.tables.RegistryEntryAuditLog",
                                // Story 10.4 — invoice-driven bootstrap jobs table
                                "hu.riskguard.jooq.tables.EprBootstrapJobs",
                                // Story 9.3 — AI classifier usage table + KF codes reference
                                "hu.riskguard.jooq.tables.AiClassifierUsage",
                                "hu.riskguard.jooq.tables.KfCodes",
                                // Story 9.3 — cross-tenant admin join (ClassifierUsageRepository.findAllForMonth)
                                "hu.riskguard.jooq.tables.Tenants",
                                // Story 9.4 — producer profiles + NAV tenant credentials join
                                "hu.riskguard.jooq.tables.ProducerProfiles",
                                "hu.riskguard.jooq.tables.NavTenantCredentials",
                                // Story 10.8 — aggregation audit log (provenance fetch + CSV export events)
                                "hu.riskguard.jooq.tables.AggregationAuditLog"
                        );
                        private static final String JOOQ_TABLES_PKG = "hu.riskguard.jooq.tables.";

                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            javaClass.getDirectDependenciesFromSelf().forEach(dep -> {
                                String targetName = dep.getTargetClass().getFullName();
                                if (targetName.startsWith(JOOQ_TABLES_PKG)) {
                                    boolean allowed = ALLOWED_TABLE_PREFIXES.stream()
                                            .anyMatch(prefix -> targetName.equals(prefix)
                                                    || targetName.startsWith(prefix + ".") // nested classes
                                                    || targetName.equals("hu.riskguard.jooq.tables.records." + extractRecordName(prefix)));
                                    if (!allowed) {
                                        events.add(SimpleConditionEvent.violated(dep,
                                                javaClass.getName() + " accesses non-owned jOOQ table " + targetName));
                                    }
                                }
                            });
                        }

                        private String extractRecordName(String tableClassName) {
                            // hu.riskguard.jooq.tables.EprConfigs → EprConfigsRecord
                            String simple = tableClassName.substring(tableClassName.lastIndexOf('.') + 1);
                            return simple + "Record";
                        }
                    });

    // No external module should access epr module's internal package.
    // Exceptions: domain facade within the same module, ArchUnit tests, and Spring AOT-generated classes.
    @ArchTest
    static final ArchRule epr_internal_should_not_be_accessed_externally =
            noClasses().that().resideOutsideOfPackage("..epr..")
                    .and().resideOutsideOfPackage("..architecture..")
                    .and().haveSimpleNameNotContaining("BeanDefinitions")
                    .and().haveSimpleNameNotContaining("BeanFactoryRegistrations")
                    .should().dependOnClassesThat().resideInAPackage("..epr.internal..");

    @ArchTest
    static final ArchRule response_dtos_should_have_from_factory =
            classes().that().resideInAPackage("..api.dto..")
                    .and().haveSimpleNameEndingWith("Response")
                    .should(new ArchCondition<JavaClass>("have a static from factory method") {
                        @Override
                        public void check(JavaClass javaClass, ConditionEvents events) {
                            boolean hasFrom = javaClass.getMethods().stream()
                                    .anyMatch(m -> m.getName().equals("from") && m.getModifiers().contains(JavaModifier.STATIC));
                            if (!hasFrom) {
                                events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " is missing static from() method"));
                            }
                        }
                    });
}
