package com.contractsys.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
        @NotBlank String roleCode,
        @NotBlank String roleName,
        String description
) {
}

