package hu.riskguard.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Table;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

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

/*
    @ArchTest
    static final ArchRule api_paths_should_match_pattern =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().beAnnotatedWith(RequestMapping.class)
                    .andShould().haveAnnotationWithAttributeValue(RequestMapping.class, "value", pattern -> {
                        for (String path : (String[]) pattern) {
                            if (!path.matches("/api/v[0-9]+/[a-z-]+")) return false;
                        }
                        return true;
                    });
*/

    @ArchTest
    static final ArchRule dtos_should_be_records =
            classes().that().resideInAPackage("..api.dto..")
                    .should().beRecords();

/*
    @ArchTest
    static final ArchRule response_dtos_should_have_from_factory =
            methods().that().arePublic().and().areStatic()
                    .and().haveName("from")
                    .and().areDeclaredInClassesThat().resideInAPackage("..api.dto..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Response")
                    .should().exist();
*/
}
