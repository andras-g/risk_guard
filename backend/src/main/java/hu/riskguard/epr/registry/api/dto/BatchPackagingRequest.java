package hu.riskguard.epr.registry.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/classifier/batch-packaging} (Story 10.3).
 *
 * <p>Up to 100 invoice-line {@code (vtsz, description)} pairs in one request.
 * The cap pre-check (controller) and bounded-concurrency Gemini calls (service)
 * are applied collectively to all pairs in the batch.
 */
public record BatchPackagingRequest(
        @NotNull @Size(min = 1, max = 100) @Valid List<PairRequest> pairs
) {

    /**
     * One invoice-line pair: VTSZ tariff code + line-item description.
     * VTSZ pattern matches {@link ClassifyRequest} for compatibility (4–8 digits).
     */
    public record PairRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{4,8}$") String vtsz,
            @NotBlank @Size(max = 500) String description
    ) {}
}
