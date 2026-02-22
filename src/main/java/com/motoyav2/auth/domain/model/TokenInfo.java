package com.motoyav2.auth.domain.model;

public record TokenInfo(
        String token,
        long expiresIn
) {
}
