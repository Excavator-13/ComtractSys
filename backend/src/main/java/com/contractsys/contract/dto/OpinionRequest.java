package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotBlank;

public record OpinionRequest(@NotBlank String opinion) {
}

