package hu.riskguard.architecture;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Epic 10 Story 10.1 AC #15 — enforcement: NAV HTTP calls must NOT sit inside an active
 * transaction in {@code hu.riskguard.epr.registry.*} or {@code hu.riskguard.epr.domain}.
 *
 * <p>Story 10.4 targets ~3000 invoices per bootstrap with ~3 s NAV HTTP latency per detail call.
 * Holding one DB connection per tenant for that duration exhausts the default Hikari pool size
 * of 10 on the second concurrent bootstrap. The Story 10.1 refactor moves NAV HTTP out of the
 * transaction; this test is the guard that prevents future drift.
 *
 * <p>Implementation: ArchUnit method-level bytecode analysis via a custom {@link ArchCondition}.
 * The condition inspects a method's actual call sites ({@link JavaMethod#getCallsFromSelf()}) —
 * so a {@code @Transactional} method that merely sits on a class holding a
 * {@code DataSourceService} field is NOT a violation unless the method body actually invokes a
 * {@code DataSourceService} (or {@code NavOnlineSzamlaClient}) method. This avoids the
 * false-positive class-level fields-check a naive reflection walker produces.
 */
@AnalyzeClasses(
        packages = "hu.riskguard",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class NavHttpOutsideTransactionTest {

    private static final String[] SCOPED_PACKAGES = {
            "..epr.registry..",
            "..epr.domain..",
            "..epr.registry.bootstrap.."  // Story 10.4 AC #15
    };

    private static final String[] FORBIDDEN_HTTP_CLIENT_SIMPLE_NAMES = {
            "DataSourceService",
            "NavOnlineSzamlaClient",
            // R3-P8: classifier invokes Vertex AI Gemini over HTTPS (seconds of latency, token
            // cost) and must also stay outside any @Transactional scope per
            // RegistryBootstrapService class-level Javadoc.
            "KfCodeClassifierService",
            // Story 10.4 AC #15: BatchPackagingClassifierService also holds VertexAI HTTP
            // calls and must stay outside any @Transactional scope.
            "BatchPackagingClassifierService"
    };

    @ArchTest
    static final ArchRule no_transactional_method_in_scope_calls_nav_http =
            noMethods()
                    .that().areAnnotatedWith(Transactional.class)
                    .and().areDeclaredInClassesThat().resideInAnyPackage(SCOPED_PACKAGES)
                    .should(invokeForbiddenHttpClient())
                    .allowEmptyShould(true);

    /**
     * Companion plain JUnit test — surfaces the same violation list via ArchUnit's programmatic
     * API, for direct investigation when CI shows only the rule's summary line.
     */
    @Test
    void noTransactionalMethodInScopeCallsNavHttp() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("hu.riskguard");
        no_transactional_method_in_scope_calls_nav_http.check(classes);
    }

    private static ArchCondition<JavaMethod> invokeForbiddenHttpClient() {
        return new ArchCondition<>("invoke a NAV/AI HTTP client "
                + "(DataSourceService, NavOnlineSzamlaClient, KfCodeClassifierService) "
                + "from within an @Transactional method") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaCall<?> call : method.getCallsFromSelf()) {
                    String owner = call.getTargetOwner().getSimpleName();
                    if (isForbiddenClient(owner)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " is @Transactional and calls "
                                        + owner + "#" + call.getTarget().getName()
                                        + " — NAV HTTP must run outside any transaction "
                                        + "(Story 10.1 AC #15)"));
                    }
                }
            }
        };
    }

    private static boolean isForbiddenClient(String ownerSimpleName) {
        for (String forbidden : FORBIDDEN_HTTP_CLIENT_SIMPLE_NAMES) {
            if (forbidden.equals(ownerSimpleName)) return true;
        }
        return false;
    }
}
