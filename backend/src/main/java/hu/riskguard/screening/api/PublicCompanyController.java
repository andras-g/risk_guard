package hu.riskguard.screening.api;

import hu.riskguard.screening.api.dto.PublicCompanyResponse;
import hu.riskguard.screening.domain.ScreeningService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public, unauthenticated REST controller for SEO gateway company data.
 * Serves public-safe company information (name, tax number, address)
 * WITHOUT authentication, WITHOUT tenant context.
 *
 * <p>Permitted via {@code /api/v1/public/**} in SecurityConfig.
 * No {@code @AuthenticationPrincipal} — this is intentionally unauthenticated.
 */
@RestController
@RequestMapping("/api/v1/public/companies")
@RequiredArgsConstructor
@Validated
public class PublicCompanyController {

    private final ScreeningService screeningService;

    /**
     * Get public-safe company data for the SEO gateway stub page.
     *
     * <p>Returns company name, tax number, and address ONLY — NO verdict,
     * NO audit hash, NO tenant data. Returns 404 if no snapshot exists.
     *
     * @param taxNumber the Hungarian tax number to look up
     * @return public-safe company data
     * @throws ResponseStatusException 404 if no snapshot exists for this tax number
     */
    @GetMapping("/{taxNumber}")
    public ResponseEntity<PublicCompanyResponse> getPublicCompanyData(
            @PathVariable @Pattern(regexp = "^\\d[\\d\\s-]{6,11}\\d$", message = "Invalid tax number format") String taxNumber) {
        PublicCompanyResponse body = screeningService.getPublicCompanyData(taxNumber)
                .map(data -> PublicCompanyResponse.from(data.taxNumber(), data.companyName(), data.address()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No company data found for the given tax number"));
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(body);
    }
}
