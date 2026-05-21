package com.contractsys.user.dto;

import com.contractsys.user.SysRole;

import java.util.List;

public record RoleView(
        Long id,
        String roleCode,
        String roleName,
        String description,
        List<PermissionBrief> permissions
) {
    public static RoleView from(SysRole role) {
        List<PermissionBrief> permissions = role.getPermissions().stream()
                .map(PermissionBrief::from)
                .toList();
        return new RoleView(role.getId(), role.getRoleCode(), role.getRoleName(),
                role.getDescription(), permissions);
    }
}
