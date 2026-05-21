package com.contractsys.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleUpdateRequest(
        @NotBlank String roleName,
        String description
) {
}

