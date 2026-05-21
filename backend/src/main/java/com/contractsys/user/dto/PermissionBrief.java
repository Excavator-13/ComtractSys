package com.contractsys.user.dto;

import com.contractsys.user.SysPermission;

public record PermissionBrief(Long id, String permissionCode, String permissionName) {
    public static PermissionBrief from(SysPermission p) {
        return new PermissionBrief(p.getId(), p.getPermissionCode(), p.getPermissionName());
    }
}
