package com.contractsys.user.dto;

public record UserUpdateRequest(
        String displayName,
        String password,
        String phone,
        String email
) {
}

