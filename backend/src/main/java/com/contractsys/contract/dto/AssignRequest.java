package com.contractsys.contract.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignRequest(
        @NotEmpty List<Long> countersignUserIds,
        @NotEmpty List<Long> approvalUserIds,
        @NotNull Long signUserId
) {
}
