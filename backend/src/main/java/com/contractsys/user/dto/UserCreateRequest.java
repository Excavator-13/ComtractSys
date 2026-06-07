package com.contractsys.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UserCreateRequest(
        @NotBlank String username,
        @NotBlank String password,
        String displayName,
        String phone,
        String email,
        List<Long> roleIds,
        Long roleId
) {
    public List<Long> effectiveRoleIds() {
        if (roleId != null) {
            return List.of(roleId);
        }
        return roleIds;
    }
}
