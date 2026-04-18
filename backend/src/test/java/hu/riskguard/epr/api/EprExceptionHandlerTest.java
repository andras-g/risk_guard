package hu.riskguard.epr.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class EprExceptionHandlerTest {

    private final EprExceptionHandler handler = new EprExceptionHandler();

    @Test
    void fkViolationOnMaterialTemplateId_mapsTo409_withStillReferencedType() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "update or delete on table \"epr_material_templates\" violates foreign key constraint "
                        + "\"product_packaging_components_material_template_id_fkey\" on table \"product_packaging_components\"",
                new RuntimeException(
                        "ERROR: update or delete on table \"epr_material_templates\" violates foreign key constraint "
                                + "\"product_packaging_components_material_template_id_fkey\" on table \"product_packaging_components\""));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(EprExceptionHandler.TEMPLATE_STILL_REFERENCED_TYPE);
        assertThat(body.getDetail()).isEqualTo(EprExceptionHandler.TEMPLATE_STILL_REFERENCED_I18N_KEY);
    }

    @Test
    void fkViolationOnUnrelatedTable_mapsTo409_butNotStillReferenced() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "update or delete on table \"some_other\" violates foreign key constraint");

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isNotEqualTo(EprExceptionHandler.TEMPLATE_STILL_REFERENCED_TYPE);
    }
}
