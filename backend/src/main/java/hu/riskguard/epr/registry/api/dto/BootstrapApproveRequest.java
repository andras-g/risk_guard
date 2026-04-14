package hu.riskguard.epr.registry.api.dto;

import hu.riskguard.epr.registry.domain.ApproveCommand;
import hu.riskguard.epr.registry.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BootstrapApproveRequest(
        String articleNumber,
        @NotBlank @Size(max = 512) String name,
        @Pattern(regexp = "^[0-9]{4,8}$") String vtsz,
        @NotBlank String primaryUnit,
        @NotNull ProductStatus status,
        @Valid @NotNull @Size(min = 1) List<ComponentUpsertRequest> components
) {

    public ApproveCommand toCommand() {
        return new ApproveCommand(
                articleNumber,
                name,
                vtsz,
                primaryUnit,
                status,
                components == null ? List.of() :
                        components.stream().map(ComponentUpsertRequest::toCommand).toList()
        );
    }
}
