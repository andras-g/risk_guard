package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.api.RegistryController;
import hu.riskguard.epr.registry.api.dto.*;
import hu.riskguard.epr.registry.domain.*;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistryController}.
 * Covers happy paths, cross-tenant 404, audit-log pagination clamp.
 */
@ExtendWith(MockitoExtension.class)
class RegistryControllerTest {

    @Mock
    private RegistryService registryService;

    private RegistryController controller;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new RegistryController(registryService);
    }

    // ─── Test 1: GET /registry returns page response ──────────────────────────

    @Test
    void list_happyPath_returnsPageResponse() {
        ProductSummary summary = buildSummary(PRODUCT_ID, "Activia 125g", ProductStatus.ACTIVE);
        when(registryService.list(eq(TENANT_ID), any(), eq(0), eq(50))).thenReturn(List.of(summary));
        when(registryService.count(eq(TENANT_ID), any())).thenReturn(1L);

        RegistryPageResponse result = controller.list(null, null, null, null, 0, 50, buildJwt());

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("Activia 125g");
    }

    // ─── Test 2: page size clamped at 200 ─────────────────────────────────────

    @Test
    void list_pageSizeOver200_clampsTo200() {
        when(registryService.list(eq(TENANT_ID), any(), eq(0), eq(200))).thenReturn(List.of());
        when(registryService.count(eq(TENANT_ID), any())).thenReturn(0L);

        RegistryPageResponse result = controller.list(null, null, null, null, 0, 9999, buildJwt());

        assertThat(result.size()).isEqualTo(200);
        verify(registryService).list(eq(TENANT_ID), any(), eq(0), eq(200));
    }

    // ─── Test 3: GET /registry/{id} happy path ───────────────────────────────

    @Test
    void get_happyPath_returnsProductResponse() {
        Product product = buildProduct(PRODUCT_ID, "Activia 125g", ProductStatus.ACTIVE);
        when(registryService.get(TENANT_ID, PRODUCT_ID)).thenReturn(product);

        ProductResponse result = controller.get(PRODUCT_ID, buildJwt());

        assertThat(result.id()).isEqualTo(PRODUCT_ID);
        assertThat(result.name()).isEqualTo("Activia 125g");
    }

    // ─── Test 4: GET cross-tenant returns 404 ────────────────────────────────

    @Test
    void get_crossTenant_propagates404() {
        when(registryService.get(TENANT_ID, PRODUCT_ID))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        assertThatThrownBy(() -> controller.get(PRODUCT_ID, buildJwt()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // ─── Test 5: POST /registry returns 201 Created response ─────────────────

    @Test
    void create_validRequest_returnsCreatedProduct() {
        ComponentUpsertRequest compReq = new ComponentUpsertRequest(
                null, "PET bottle", "11020101", new BigDecimal("0.45"),
                0, new BigDecimal("1"), 1, null,
                null, null, null, null, null, null, null, null);
        ProductUpsertRequest request = new ProductUpsertRequest(
                "ART-001", "Activia 125g", "3923", "pcs",
                ProductStatus.ACTIVE, List.of(compReq));
        Product createdProduct = buildProduct(PRODUCT_ID, "Activia 125g", ProductStatus.ACTIVE);

        when(registryService.create(eq(TENANT_ID), eq(USER_ID), any())).thenReturn(createdProduct);

        ProductResponse result = controller.create(request, buildJwtWithUser());

        assertThat(result.id()).isEqualTo(PRODUCT_ID);
        assertThat(result.name()).isEqualTo("Activia 125g");
    }

    // ─── Test 5b: negative page is clamped to 0 ──────────────────────────────

    @Test
    void list_negativePage_clampsToZero() {
        when(registryService.list(eq(TENANT_ID), any(), eq(0), eq(50))).thenReturn(List.of());
        when(registryService.count(eq(TENANT_ID), any())).thenReturn(0L);

        RegistryPageResponse result = controller.list(null, null, null, null, -5, 50, buildJwt());

        assertThat(result.page()).isEqualTo(0);
        verify(registryService).list(eq(TENANT_ID), any(), eq(0), eq(50));
    }

    // ─── Test 5c: size <= 0 is clamped to 1 ──────────────────────────────────

    @Test
    void list_zeroSize_clampsToOne() {
        when(registryService.list(eq(TENANT_ID), any(), eq(0), eq(1))).thenReturn(List.of());
        when(registryService.count(eq(TENANT_ID), any())).thenReturn(0L);

        RegistryPageResponse result = controller.list(null, null, null, null, 0, 0, buildJwt());

        assertThat(result.size()).isEqualTo(1);
        verify(registryService).list(eq(TENANT_ID), any(), eq(0), eq(1));
    }

    // ─── Test 6: audit-log endpoint clamps page size ──────────────────────────

    @Test
    void auditLog_pageSizeOver200_clampsTo200() {
        when(registryService.listAuditLog(eq(TENANT_ID), eq(PRODUCT_ID), eq(0), eq(200)))
                .thenReturn(List.of());
        when(registryService.countAuditLog(eq(TENANT_ID), eq(PRODUCT_ID))).thenReturn(0L);

        RegistryAuditPageResponse result = controller.auditLog(PRODUCT_ID, 0, 9999, buildJwt());

        assertThat(result.size()).isEqualTo(200);
        verify(registryService).listAuditLog(eq(TENANT_ID), eq(PRODUCT_ID), eq(0), eq(200));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Jwt buildJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }

    private Jwt buildJwtWithUser() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("test@test.com")
                .claim("active_tenant_id", TENANT_ID.toString())
                .claim("user_id", USER_ID.toString())
                .claim("role", "SME_ADMIN")
                .build();
    }

    private ProductSummary buildSummary(UUID id, String name, ProductStatus status) {
        return new ProductSummary(id, TENANT_ID, null, name, null, "pcs", status, 0,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private Product buildProduct(UUID id, String name, ProductStatus status) {
        return new Product(id, TENANT_ID, null, name, null, "pcs", status, List.of(),
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
