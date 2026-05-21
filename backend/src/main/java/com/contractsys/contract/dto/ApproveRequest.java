package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApproveRequest(
        @NotNull ApproveResult result,
        @NotBlank String opinion
) {
}

