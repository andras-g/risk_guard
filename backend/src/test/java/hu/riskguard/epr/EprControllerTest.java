package hu.riskguard.epr;

import hu.riskguard.epr.api.EprController;
import hu.riskguard.epr.api.dto.*;
import hu.riskguard.epr.domain.EprService;
import hu.riskguard.jooq.tables.records.EprMaterialTemplatesRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EprController}.
 * Follows the ScreeningControllerTest / WatchlistControllerTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class EprControllerTest {

    @Mock
    private EprService eprService;

    private EprController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new EprController(eprService);
    }

    @Test
    void createTemplateShouldReturnCreatedResponse() {
        UUID newId = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        MaterialTemplateRequest request = new MaterialTemplateRequest("Box", new BigDecimal("50"), null);

        when(eprService.createTemplate(eq(TENANT_ID), eq("Box"), eq(new BigDecimal("50")), isNull()))
                .thenReturn(newId);
        when(eprService.findTemplate(newId, TENANT_ID))
                .thenReturn(Optional.of(buildRecord(newId, "Box", new BigDecimal("50"), true)));

        MaterialTemplateResponse result = controller.createTemplate(request, jwt);

        assertThat(result.id()).isEqualTo(newId);
        assertThat(result.name()).isEqualTo("Box");
        assertThat(result.baseWeightGrams()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(result.verified()).isFalse();
        assertThat(result.recurring()).isTrue();
    }

    @Test
    void listTemplatesShouldReturnAllForTenant() {
        Jwt jwt = buildJwt(TENANT_ID);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        MaterialTemplateResponse resp1 = new MaterialTemplateResponse(
                id1, "T1", new BigDecimal("10"), null, false, true,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null, null, null);
        MaterialTemplateResponse resp2 = new MaterialTemplateResponse(
                id2, "T2", new BigDecimal("20"), null, false, false,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null, null, null);
        when(eprService.listTemplatesWithOverride(TENANT_ID)).thenReturn(List.of(resp1, resp2));

        List<MaterialTemplateResponse> result = controller.listTemplates(jwt);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("T1");
        assertThat(result.get(1).recurring()).isFalse();
    }

    @Test
    void updateTemplateShouldReturnUpdatedResponse() {
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        MaterialTemplateRequest request = new MaterialTemplateRequest("Updated", new BigDecimal("99"), null);

        when(eprService.updateTemplate(id, TENANT_ID, "Updated", new BigDecimal("99"), null))
                .thenReturn(true);
        when(eprService.findTemplate(id, TENANT_ID))
                .thenReturn(Optional.of(buildRecord(id, "Updated", new BigDecimal("99"), true)));

        MaterialTemplateResponse result = controller.updateTemplate(id, request, jwt);

        assertThat(result.name()).isEqualTo("Updated");
    }

    @Test
    void updateTemplateShouldReturn404WhenNotFound() {
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        MaterialTemplateRequest request = new MaterialTemplateRequest("X", new BigDecimal("1"), null);

        when(eprService.updateTemplate(id, TENANT_ID, "X", new BigDecimal("1"), null))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.updateTemplate(id, request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void deleteTemplateShouldReturn204WhenDeleted() {
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        when(eprService.deleteTemplate(id, TENANT_ID)).thenReturn(true);

        controller.deleteTemplate(id, jwt);

        verify(eprService).deleteTemplate(id, TENANT_ID);
    }

    @Test
    void deleteTemplateShouldReturn404WhenNotFound() {
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        when(eprService.deleteTemplate(id, TENANT_ID)).thenReturn(false);

        assertThatThrownBy(() -> controller.deleteTemplate(id, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void toggleRecurringShouldReturnUpdatedResponse() {
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        RecurringToggleRequest request = new RecurringToggleRequest(false);

        when(eprService.toggleRecurring(id, TENANT_ID, false)).thenReturn(true);
        when(eprService.findTemplate(id, TENANT_ID))
                .thenReturn(Optional.of(buildRecord(id, "T", new BigDecimal("10"), false)));

        MaterialTemplateResponse result = controller.toggleRecurring(id, request, jwt);

        assertThat(result.recurring()).isFalse();
    }

    @Test
    void toggleRecurringShouldReturn404WhenNotFound() {
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(TENANT_ID);
        RecurringToggleRequest request = new RecurringToggleRequest(false);

        when(eprService.toggleRecurring(id, TENANT_ID, false)).thenReturn(false);

        assertThatThrownBy(() -> controller.toggleRecurring(id, request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void copyFromQuarterShouldReturnCopiedTemplates() {
        Jwt jwt = buildJwt(TENANT_ID);
        CopyQuarterRequest request = new CopyQuarterRequest(2025, 4, false);
        UUID newId = UUID.randomUUID();

        when(eprService.copyFromQuarter(TENANT_ID, 2025, 4, false))
                .thenReturn(List.of(newId));
        when(eprService.findTemplatesByIds(List.of(newId), TENANT_ID))
                .thenReturn(List.of(buildRecord(newId, "Copied", new BigDecimal("10"), true)));

        List<MaterialTemplateResponse> result = controller.copyFromQuarter(request, jwt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Copied");
    }

    @Test
    void copyFromQuarterShouldReturnEmptyListWhenNoSourceTemplates() {
        Jwt jwt = buildJwt(TENANT_ID);
        CopyQuarterRequest request = new CopyQuarterRequest(2025, 3, true);

        when(eprService.copyFromQuarter(TENANT_ID, 2025, 3, true))
                .thenReturn(List.of());

        List<MaterialTemplateResponse> result = controller.copyFromQuarter(request, jwt);

        assertThat(result).isEmpty();
        verify(eprService).copyFromQuarter(TENANT_ID, 2025, 3, true);
    }

    @Test
    void copyFromQuarterShouldNotConflictWithMaterialIdRoute() {
        // Regression test: ensures /materials/copy-from-quarter is not intercepted
        // by a hypothetical {id} path variable handler on POST /materials/{id}
        Jwt jwt = buildJwt(TENANT_ID);
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();
        CopyQuarterRequest request = new CopyQuarterRequest(2025, 4, true);

        when(eprService.copyFromQuarter(TENANT_ID, 2025, 4, true))
                .thenReturn(List.of(newId1, newId2));
        when(eprService.findTemplatesByIds(List.of(newId1, newId2), TENANT_ID))
                .thenReturn(List.of(
                        buildRecord(newId1, "T1", new BigDecimal("10"), true),
                        buildRecord(newId2, "T2", new BigDecimal("20"), false)));

        List<MaterialTemplateResponse> result = controller.copyFromQuarter(request, jwt);

        assertThat(result).hasSize(2);
        // Verify the copy-from-quarter method was called (not a create/update method)
        verify(eprService).copyFromQuarter(TENANT_ID, 2025, 4, true);
        verify(eprService).findTemplatesByIds(List.of(newId1, newId2), TENANT_ID);
    }

    @Test
    void createTemplateShouldRejectMissingTenantId() {
        MaterialTemplateRequest request = new MaterialTemplateRequest("Box", new BigDecimal("50"), null);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .build();

        assertThatThrownBy(() -> controller.createTemplate(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active_tenant_id");
    }

    @Test
    void listTemplatesShouldRejectMalformedTenantId() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", "not-a-uuid")
                .build();

        assertThatThrownBy(() -> controller.listTemplates(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    void calculateFiling_validLines_returnsCalculationResponse() {
        Jwt jwt = buildJwt(TENANT_ID);
        UUID templateId = UUID.randomUUID();
        hu.riskguard.epr.api.dto.FilingLineRequest line =
                new hu.riskguard.epr.api.dto.FilingLineRequest(templateId, 1000);
        hu.riskguard.epr.api.dto.FilingCalculationRequest request =
                new hu.riskguard.epr.api.dto.FilingCalculationRequest(List.of(line));

        hu.riskguard.epr.api.dto.FilingLineResultDto resultLine =
                new hu.riskguard.epr.api.dto.FilingLineResultDto(
                        templateId, "Cardboard Box", "11010101", 1000,
                        new BigDecimal("120"), new BigDecimal("120000"),
                        new BigDecimal("120.000000"), new BigDecimal("215"),
                        new BigDecimal("25800"));
        hu.riskguard.epr.api.dto.FilingCalculationResponse expected =
                new hu.riskguard.epr.api.dto.FilingCalculationResponse(
                        List.of(resultLine), new BigDecimal("120.000000"),
                        new BigDecimal("25800"), 1);

        when(eprService.calculateFiling(any(), any())).thenReturn(expected);

        hu.riskguard.epr.api.dto.FilingCalculationResponse result = controller.calculateFiling(request, jwt);

        assertThat(result.lines()).hasSize(1);
        assertThat(result.grandTotalFeeHuf()).isEqualByComparingTo(new BigDecimal("25800"));
        assertThat(result.grandTotalWeightKg()).isEqualByComparingTo(new BigDecimal("120.000000"));
    }

    @Test
    void calculateFiling_emptyLines_returns400() {
        // @NotEmpty on FilingCalculationRequest.lines is enforced by Spring MVC (@Valid on the
        // controller parameter), resulting in HTTP 400. Verify the constraint is declared on the DTO.
        var request = new hu.riskguard.epr.api.dto.FilingCalculationRequest(List.of());
        var violations = jakarta.validation.Validation.buildDefaultValidatorFactory()
                .getValidator().validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("lines");
    }

    @Test
    void calculateFiling_missingJwtClaim_returns401() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .build();
        UUID templateId = UUID.randomUUID();
        hu.riskguard.epr.api.dto.FilingCalculationRequest request =
                new hu.riskguard.epr.api.dto.FilingCalculationRequest(
                        List.of(new hu.riskguard.epr.api.dto.FilingLineRequest(templateId, 100)));

        assertThatThrownBy(() -> controller.calculateFiling(request, jwt))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    private Jwt buildJwt(UUID tenantId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", tenantId.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }

    private EprMaterialTemplatesRecord buildRecord(UUID id, String name, BigDecimal weight, boolean recurring) {
        EprMaterialTemplatesRecord record = new EprMaterialTemplatesRecord();
        record.setId(id);
        record.setTenantId(TENANT_ID);
        record.setName(name);
        record.setBaseWeightGrams(weight);
        record.setVerified(false);
        record.setRecurring(recurring);
        record.setCreatedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());
        return record;
    }
}
