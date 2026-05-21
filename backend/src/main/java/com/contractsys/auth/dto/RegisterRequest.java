package com.contractsys.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 40) String username,
        @NotBlank @Size(max = 100) String password,
        @NotBlank @Size(max = 100) String confirmPassword
) {
}
