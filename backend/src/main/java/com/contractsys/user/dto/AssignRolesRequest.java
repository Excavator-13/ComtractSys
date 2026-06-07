package com.contractsys.user.dto;

import java.util.List;

public record AssignRolesRequest(Long roleId, List<Long> roleIds) {
    public List<Long> effectiveRoleIds() {
        if (roleId != null) {
            return List.of(roleId);
        }
        return roleIds;
    }
}
