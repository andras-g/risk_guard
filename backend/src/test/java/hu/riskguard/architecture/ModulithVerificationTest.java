package hu.riskguard.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithVerificationTest {

    ApplicationModules modules = ApplicationModules.of("hu.riskguard");

    @Test
    void verifyModulith() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
