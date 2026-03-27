package hu.riskguard.epr.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FilingCalculationRequest(
        @NotEmpty @Valid List<FilingLineRequest> lines
) {}
