package hu.riskguard.epr;

import hu.riskguard.epr.api.dto.*;
import hu.riskguard.epr.domain.EprService;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static hu.riskguard.jooq.Tables.EPR_CALCULATIONS;
import static hu.riskguard.jooq.Tables.EPR_CONFIGS;
import static hu.riskguard.jooq.Tables.EPR_EXPORTS;
import static hu.riskguard.jooq.Tables.EPR_MATERIAL_TEMPLATES;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test baseline for the EPR module.
 * Verifies Spring context loads, Flyway migrations apply, jOOQ codegen succeeded, and seed data exists.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EprModuleIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private EprService eprService;

    @Test
    void contextLoadsWithEprModule() {
        // EprService should be wired and operational
        assertNotNull(eprService);
        assertTrue(eprService.isHealthy());
    }

    @Test
    void eprTablesExistAfterMigration() {
        // Verify jOOQ codegen succeeded — these static references would fail to compile if codegen missed them (AC 6)
        assertEquals("epr_configs", EPR_CONFIGS.getName());
        assertEquals("epr_material_templates", EPR_MATERIAL_TEMPLATES.getName());
        assertEquals("epr_calculations", EPR_CALCULATIONS.getName());
        assertEquals("epr_exports", EPR_EXPORTS.getName());

        // Verify tables actually exist in the database via information_schema (no tenant context needed)
        var tables = dsl.select(DSL.field("table_name", String.class))
                .from(DSL.table("information_schema.tables"))
                .where(DSL.field("table_schema").eq("public"))
                .and(DSL.field("table_name").like("epr_%"))
                .fetch(DSL.field("table_name", String.class));

        assertTrue(tables.contains("epr_configs"), "epr_configs table should exist in database");
        assertTrue(tables.contains("epr_material_templates"), "epr_material_templates table should exist in database");
        assertTrue(tables.contains("epr_calculations"), "epr_calculations table should exist in database");
        assertTrue(tables.contains("epr_exports"), "epr_exports table should exist in database");
        assertTrue(tables.size() >= 4, "Expected at least 4 EPR tables (more may be added by future stories)");
    }

    @Test
    void seedDataExists() {
        // Verify the v1 EPR config seed record exists using type-safe jOOQ references
        Record1<Integer> count = dsl.selectCount()
                .from(EPR_CONFIGS)
                .where(EPR_CONFIGS.VERSION.eq(1))
                .and(EPR_CONFIGS.SCHEMA_VERIFIED.eq(true))
                .fetchOne();

        assertNotNull(count);
        assertEquals(1, count.value1(),
                "Expected exactly 1 seed config record with version=1");
    }

    @Test
    void seedDataContainsExpectedJsonStructure() {
        // Verify the config_data JSONB has the expected top-level sections using type-safe jOOQ references
        String configData = dsl.select(EPR_CONFIGS.CONFIG_DATA.cast(String.class))
                .from(EPR_CONFIGS)
                .where(EPR_CONFIGS.VERSION.eq(1))
                .fetchOne(EPR_CONFIGS.CONFIG_DATA.cast(String.class));

        assertNotNull(configData, "config_data should not be null");
        assertTrue(configData.contains("kf_code_structure"),
                "config_data should contain kf_code_structure");
        assertTrue(configData.contains("fee_rates_2026"),
                "config_data should contain fee_rates_2026");
        assertTrue(configData.contains("fee_modulation"),
                "config_data should contain fee_modulation");
    }

    // ─── DagEngine traversal smoke tests against real seed data ─────────────

    /**
     * Golden case 1: Non-deposit paper consumer packaging.
     * Path: 11 → 01 → 01 → 01 = KF code 11010101, díjkód 1101, 20.44 Ft/kg
     */
    @Test
    void dagEngine_case1_nonDepositPaperConsumerPackaging() {
        int configVersion = eprService.getActiveConfigVersion();

        // Start: get product streams
        WizardStartResponse start = eprService.startWizard(configVersion, "hu");
        assertNotNull(start);
        assertTrue(start.options().stream().anyMatch(o -> "11".equals(o.code())),
                "Product stream '11' (non-deposit packaging) should be available");

        // Step 1: select product stream 11 (non-deposit packaging)
        WizardStepRequest step1Request = new WizardStepRequest(configVersion, List.of(),
                new WizardSelection("product_stream", "11", "Csomagolás"));
        WizardStepResponse step1 = eprService.processStep(step1Request, "hu");
        assertTrue(step1.options().stream().anyMatch(o -> "01".equals(o.code())),
                "Material stream '01' (paper) should be available");

        // Step 2: select material stream 01 (paper)
        WizardStepRequest step2Request = new WizardStepRequest(configVersion,
                List.of(new WizardSelection("product_stream", "11", "Csomagolás")),
                new WizardSelection("material_stream", "01", "Papír"));
        WizardStepResponse step2 = eprService.processStep(step2Request, "hu");
        assertTrue(step2.options().stream().anyMatch(o -> "01".equals(o.code())),
                "Group '01' (consumer) should be available");

        // Step 3: select group 01 (consumer)
        WizardStepRequest step3Request = new WizardStepRequest(configVersion,
                List.of(new WizardSelection("product_stream", "11", "Csomagolás"),
                        new WizardSelection("material_stream", "01", "Papír")),
                new WizardSelection("group", "01", "Fogyasztói"));
        WizardStepResponse step3 = eprService.processStep(step3Request, "hu");
        assertTrue(step3.options().stream().anyMatch(o -> "01".equals(o.code())),
                "Subgroup '01' should be available");

        // Resolve: full 4-level traversal
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "11", "Csomagolás"),
                new WizardSelection("material_stream", "01", "Papír"),
                new WizardSelection("group", "01", "Fogyasztói"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("11010101", resolved.kfCode(), "Golden case 1: KF code should be 11010101");
        assertEquals("1101", resolved.feeCode(), "Golden case 1: díjkód should be 1101");
        assertEquals(new BigDecimal("20.44"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 1: fee rate should be 20.44 Ft/kg");
    }

    /**
     * Golden case 2: Non-deposit plastic transport packaging.
     * Path: 11 → 02 → 03 → 01 = KF code 11020301, díjkód 1102, 42.89 Ft/kg
     */
    @Test
    void dagEngine_case2_nonDepositPlasticTransportPackaging() {
        int configVersion = eprService.getActiveConfigVersion();
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "11", "Csomagolás"),
                new WizardSelection("material_stream", "02", "Műanyag"),
                new WizardSelection("group", "03", "Szállítói"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("11020301", resolved.kfCode(), "Golden case 2: KF code should be 11020301");
        assertEquals("1102", resolved.feeCode(), "Golden case 2: díjkód should be 1102");
        assertEquals(new BigDecimal("42.89"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 2: fee rate should be 42.89 Ft/kg");
    }

    /**
     * Golden case 3: Deposit PET bottle plastic.
     * Path: 12 → 02 → 01 → 01 = KF code 12020101, díjkód 1202, 42.89 Ft/kg
     */
    @Test
    void dagEngine_case3_depositPetBottlePlastic() {
        int configVersion = eprService.getActiveConfigVersion();
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "12", "Kötelező visszaváltás"),
                new WizardSelection("material_stream", "02", "Műanyag"),
                new WizardSelection("group", "01", "PET palack"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("12020101", resolved.kfCode(), "Golden case 3: KF code should be 12020101");
        assertEquals("1202", resolved.feeCode(), "Golden case 3: díjkód should be 1202");
        assertEquals(new BigDecimal("42.89"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 3: fee rate should be 42.89 Ft/kg");
    }

    /**
     * Golden case 4: Large household appliance (EEE category 1 — refrigerator).
     * Path: 21 → 01 → 01 → 01 = KF code 21010101, díjkód 2101, 22.26 Ft/kg
     */
    @Test
    void dagEngine_case4_largeHouseholdApplianceEee() {
        int configVersion = eprService.getActiveConfigVersion();
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "21", "Nagyhá tartós"),
                new WizardSelection("material_stream", "01", "EEE kat. 1"),
                new WizardSelection("group", "01", "Alapértelmezett"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("21010101", resolved.kfCode(), "Golden case 4: KF code should be 21010101");
        assertEquals("2101", resolved.feeCode(), "Golden case 4: díjkód should be 2101");
        assertEquals(new BigDecimal("22.26"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 4: fee rate should be 22.26 Ft/kg");
    }

    /**
     * Golden case 5: Portable battery (button cell).
     * Path: 31 → 01 → 01 → 01 = KF code 31010101, díjkód 3101, 189.02 Ft/kg
     */
    @Test
    void dagEngine_case5_portableBattery() {
        int configVersion = eprService.getActiveConfigVersion();
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "31", "Hordozható elemek"),
                new WizardSelection("material_stream", "01", "Hordozható"),
                new WizardSelection("group", "01", "Alapértelmezett"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("31010101", resolved.kfCode(), "Golden case 5: KF code should be 31010101");
        assertEquals("3101", resolved.feeCode(), "Golden case 5: díjkód should be 3101");
        assertEquals(new BigDecimal("189.02"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 5: fee rate should be 189.02 Ft/kg");
    }

    /**
     * Golden case 6: Passenger car tire (new).
     * Path: 41 → 01 → 01 → 01 = KF code 41010101, díjkód 4101, 30.62 Ft/kg
     */
    @Test
    void dagEngine_case6_passengerCarTire() {
        int configVersion = eprService.getActiveConfigVersion();
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "41", "Gumiabroncs"),
                new WizardSelection("material_stream", "01", "Alapértelmezett"),
                new WizardSelection("group", "01", "Személygépkocsi"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("41010101", resolved.kfCode(), "Golden case 6: KF code should be 41010101");
        assertEquals("4101", resolved.feeCode(), "Golden case 6: díjkód should be 4101");
        assertEquals(new BigDecimal("30.62"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 6: fee rate should be 30.62 Ft/kg");
    }

    /**
     * Golden case 7: Single-use EPS plastic.
     * Path: 81 → 02 → 01 → 01 = KF code 81020101, díjkód 8102, 1908.78 Ft/kg
     */
    @Test
    void dagEngine_case7_singleUseEpsPlastic() {
        int configVersion = eprService.getActiveConfigVersion();
        List<WizardSelection> fullPath = List.of(
                new WizardSelection("product_stream", "81", "Egyszer használatos"),
                new WizardSelection("material_stream", "02", "EPS"),
                new WizardSelection("group", "01", "Alapértelmezett"),
                new WizardSelection("subgroup", "01", "Alapértelmezett")
        );
        WizardResolveResponse resolved = eprService.resolveKfCode(fullPath, configVersion);
        assertEquals("81020101", resolved.kfCode(), "Golden case 7: KF code should be 81020101");
        assertEquals("8102", resolved.feeCode(), "Golden case 7: díjkód should be 8102");
        assertEquals(new BigDecimal("1908.78"), resolved.feeRate().stripTrailingZeros(),
                "Golden case 7: fee rate should be 1908.78 Ft/kg");
    }
}
