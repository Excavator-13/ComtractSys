package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReturnRequest(
        @NotBlank @Pattern(regexp = "DRAFT|FINALIZE") String targetStage,
        @NotBlank String opinion
) {
}
