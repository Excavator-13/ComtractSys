package com.contractsys.user.dto;

import com.contractsys.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {
}

