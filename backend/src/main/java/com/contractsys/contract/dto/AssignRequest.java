package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssignRequest(
        @NotEmpty List<Long> countersignUserIds,
        @NotEmpty List<Long> approvalUserIds,
        @NotEmpty List<Long> signUserIds
) {
}

