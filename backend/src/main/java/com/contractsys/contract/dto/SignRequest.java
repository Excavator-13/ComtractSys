package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SignRequest(
        @NotBlank String signInfo,
        @NotNull LocalDate signedDate
) {
}

