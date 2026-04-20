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
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String phone,
        String userType,
        String userCategory,
        boolean firstLogin,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<StoreInfoDto> stores,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> modulos
) {
}
