package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.PublicCompanyResponse;
import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.PublicCompanyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the public company data endpoint (Story 3.11).
 * Verifies: 200 with public data (no auth needed), 404 for unknown tax number,
 * no verdict/audit fields in response.
 */
@ExtendWith(MockitoExtension.class)
class ScreeningControllerPublicTest {

    @Mock
    private ScreeningService screeningService;

    private PublicCompanyController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicCompanyController(screeningService);
    }

    @Test
    void shouldReturnPublicCompanyDataForKnownTaxNumber() {
        // Given
        String taxNumber = "12345678";
        PublicCompanyData domainData = new PublicCompanyData(
                taxNumber, "Test Company Kft.", "1234 Budapest, Test utca 1.");
        when(screeningService.getPublicCompanyData(eq(taxNumber)))
                .thenReturn(Optional.of(domainData));

        // When
        ResponseEntity<PublicCompanyResponse> response = controller.getPublicCompanyData(taxNumber);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PublicCompanyResponse result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.taxNumber()).isEqualTo(taxNumber);
        assertThat(result.companyName()).isEqualTo("Test Company Kft.");
        assertThat(result.address()).isEqualTo("1234 Budapest, Test utca 1.");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=3600");
        verify(screeningService).getPublicCompanyData(taxNumber);
    }

    @Test
    void shouldReturn404ForUnknownTaxNumber() {
        // Given
        String unknownTaxNumber = "99999999";
        when(screeningService.getPublicCompanyData(eq(unknownTaxNumber)))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> controller.getPublicCompanyData(unknownTaxNumber))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void shouldReturnPublicDataWithNullCompanyNameAndAddress() {
        // Given — snapshot exists but no company name or address in data
        String taxNumber = "12345678";
        PublicCompanyData domainData = new PublicCompanyData(taxNumber, null, null);
        when(screeningService.getPublicCompanyData(eq(taxNumber)))
                .thenReturn(Optional.of(domainData));

        // When
        ResponseEntity<PublicCompanyResponse> response = controller.getPublicCompanyData(taxNumber);

        // Then — still returns 200, just with null optional fields
        PublicCompanyResponse result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.taxNumber()).isEqualTo(taxNumber);
        assertThat(result.companyName()).isNull();
        assertThat(result.address()).isNull();
    }

    @Test
    void responseShouldNotContainVerdictOrAuditFields() {
        // Given
        String taxNumber = "12345678";
        PublicCompanyResponse response = PublicCompanyResponse.from(
                taxNumber, "Company Kft.", null);

        // Then — verify the record type has ONLY 3 fields: taxNumber, companyName, address
        // No verdict, no hash, no tenant data
        assertThat(PublicCompanyResponse.class.getRecordComponents()).hasSize(3);
        assertThat(response.taxNumber()).isEqualTo(taxNumber);
        assertThat(response.companyName()).isEqualTo("Company Kft.");
        assertThat(response.address()).isNull();
    }
}
