package com.contractsys.user.dto;

import com.contractsys.user.SysPermission;

public record PermissionView(
        Long id,
        String permissionCode,
        String permissionName,
        String module,
        String url,
        String description
) {
    public static PermissionView from(SysPermission p) {
        return new PermissionView(p.getId(), p.getPermissionCode(), p.getPermissionName(),
                p.getModule(), p.getUrl(), p.getDescription());
    }
}
