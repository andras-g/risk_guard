package hu.riskguard.screening.api;

import hu.riskguard.screening.domain.ScreeningService;
import hu.riskguard.screening.domain.ScreeningService.PublicCompanyData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc-level test for {@link PublicCompanyController} verifying HTTP-level behavior:
 * Spring MVC dispatching, JSON serialization, Cache-Control headers, and 404 handling.
 *
 * <p>Uses standalone MockMvc (no Spring context) — this is a controller-layer unit test,
 * not a true integration test. {@code @Pattern} validation is NOT enforced in standalone
 * mode; real validation requires {@code @WebMvcTest} or {@code @SpringBootTest}.
 */
@ExtendWith(MockitoExtension.class)
class PublicCompanyControllerIntegrationTest {

    @Mock
    private ScreeningService screeningService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicCompanyController(screeningService))
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturn200WithPublicCompanyDataAndCacheHeader() throws Exception {
        // Given
        String taxNumber = "12345678";
        PublicCompanyData domainData = new PublicCompanyData(
                taxNumber, "Test Company Kft.", "1234 Budapest, Test utca 1.");
        when(screeningService.getPublicCompanyData(eq(taxNumber)))
                .thenReturn(Optional.of(domainData));

        // When / Then
        mockMvc.perform(get("/api/v1/public/companies/{taxNumber}", taxNumber)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=3600"))
                .andExpect(jsonPath("$.taxNumber").value(taxNumber))
                .andExpect(jsonPath("$.companyName").value("Test Company Kft."))
                .andExpect(jsonPath("$.address").value("1234 Budapest, Test utca 1."));
    }

    @Test
    void shouldReturn404ForUnknownTaxNumber() throws Exception {
        // Given
        String unknownTaxNumber = "99999999";
        when(screeningService.getPublicCompanyData(eq(unknownTaxNumber)))
                .thenReturn(Optional.empty());

        // When / Then
        mockMvc.perform(get("/api/v1/public/companies/{taxNumber}", unknownTaxNumber)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnValidJsonStructureWithNullFields() throws Exception {
        // Given — snapshot exists but no company name or address
        String taxNumber = "12345678";
        PublicCompanyData domainData = new PublicCompanyData(taxNumber, null, null);
        when(screeningService.getPublicCompanyData(eq(taxNumber)))
                .thenReturn(Optional.of(domainData));

        // When / Then — JSON has null fields but still 200
        mockMvc.perform(get("/api/v1/public/companies/{taxNumber}", taxNumber)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxNumber").value(taxNumber))
                .andExpect(jsonPath("$.companyName").isEmpty())
                .andExpect(jsonPath("$.address").isEmpty());
    }

    @Test
    void shouldReturn200WithNullCompanyNameFallback() throws Exception {
        // Given — snapshot exists but tax number used as display fallback
        String taxNumber = "12345678";
        PublicCompanyData domainData = new PublicCompanyData(taxNumber, null, "1234 Budapest");
        when(screeningService.getPublicCompanyData(eq(taxNumber)))
                .thenReturn(Optional.of(domainData));

        // When / Then — verify partial data renders correctly
        mockMvc.perform(get("/api/v1/public/companies/{taxNumber}", taxNumber)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxNumber").value(taxNumber))
                .andExpect(jsonPath("$.companyName").isEmpty())
                .andExpect(jsonPath("$.address").value("1234 Budapest"));
    }
}
