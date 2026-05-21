package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContractCreateRequest(
        @NotBlank String name,
        @NotNull Long customerId,
        @NotNull LocalDate beginDate,
        @NotNull LocalDate endDate,
        @NotBlank String content
) {
}

