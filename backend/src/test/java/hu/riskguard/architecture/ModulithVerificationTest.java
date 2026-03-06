package hu.riskguard.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import hu.riskguard.RiskGuardApplication;

class ModulithVerificationTest {

    ApplicationModules modules = ApplicationModules.of(RiskGuardApplication.class);

    @Test
    void verifyModulith() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
