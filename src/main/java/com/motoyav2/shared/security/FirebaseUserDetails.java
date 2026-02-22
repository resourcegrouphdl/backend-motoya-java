package com.motoyav2.shared.security;

import java.util.Map;

public record FirebaseUserDetails(
        String uid,
        String email,
        boolean emailVerified,
        Map<String, Object> claims
) {
}
