package com.contractsys.auth.dto;

import com.contractsys.user.SysPermission;
import com.contractsys.user.SysRole;
import com.contractsys.user.SysUser;

import java.util.List;

public record UserView(
        Long id,
        String username,
        String displayName,
        String phone,
        String email,
        String status,
        List<String> roles,
        List<String> permissions
) {
    public static UserView from(SysUser user) {
        List<String> roles = user.getRoles().stream().map(SysRole::getRoleCode).sorted().toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(SysPermission::getPermissionCode)
                .distinct()
                .sorted()
                .toList();
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getPhone(), user.getEmail(), user.getStatus().name(),
                roles, permissions);
    }
}
