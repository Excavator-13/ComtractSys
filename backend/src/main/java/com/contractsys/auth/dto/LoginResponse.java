package com.contractsys.auth.dto;

public record LoginResponse(String token, UserView user) {
}

