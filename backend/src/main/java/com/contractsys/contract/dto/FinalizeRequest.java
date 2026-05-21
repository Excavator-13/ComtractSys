package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotBlank;

public record FinalizeRequest(@NotBlank String content) {
}

