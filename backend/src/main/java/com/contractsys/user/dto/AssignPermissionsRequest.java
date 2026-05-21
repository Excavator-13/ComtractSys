package com.contractsys.user.dto;

import java.util.List;

public record AssignPermissionsRequest(List<Long> permissionIds) {
}

