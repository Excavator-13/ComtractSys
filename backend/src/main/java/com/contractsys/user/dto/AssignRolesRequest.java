package com.contractsys.user.dto;

import java.util.List;

public record AssignRolesRequest(List<Long> roleIds) {
}

