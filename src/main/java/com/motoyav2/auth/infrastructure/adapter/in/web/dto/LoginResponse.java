package com.motoyav2.auth.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record LoginResponse(
        String token,
        long expiresIn,
        String uid,
        String firstName,
        String lastName,
        String email,
        String userType,
        boolean firstLogin,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<StoreInfoDto> stores
) {
}
