package com.contractsys.user.dto;

import com.contractsys.user.SysRole;

public record RoleOptionView(
        String roleCode,
        String roleName
) {
    public static RoleOptionView from(SysRole role) {
        return new RoleOptionView(role.getRoleCode(), role.getRoleName());
    }
}
