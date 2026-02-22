package com.motoyav2.auth.domain.model;

import java.util.List;

public record LoginResult(
        TokenInfo tokenInfo,
        User user,
        List<Store> stores
) {
}
