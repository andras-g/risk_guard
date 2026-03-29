package hu.riskguard.epr.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MohuExportRequest(
        @NotEmpty List<@Valid ExportLineRequest> lines,
        @NotNull int configVersion
) {}
