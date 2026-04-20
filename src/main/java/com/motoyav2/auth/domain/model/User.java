package com.motoyav2.auth.domain.model;

import java.util.List;

public record User(
        String uid,
        String firstName,
        String lastName,
        String email,
        String phone,
        String userType,
        String userCategory,
        boolean active,
        boolean firstLogin,
        List<String> storeIds,
        List<String> modulos
) {
}
