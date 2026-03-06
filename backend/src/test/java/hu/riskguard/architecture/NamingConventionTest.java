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
                                if (!path.matches("/api/v[0-9]+/[a-z-]+")) {
                                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " has invalid path " + path));
                                }
                            }
                        }
                    });

    @ArchTest
    static final ArchRule dtos_should_be_records =
            classes().that().resideInAPackage("..api.dto..")
                    .should().beRecords();

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
