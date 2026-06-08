package com.contractsys.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserCreateRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6, max = 100) String password,
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
