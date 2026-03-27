package com.motoyav2.alertascenter.domain.model;

import java.time.Instant;

public record DeclineEntry(
        String userId,
        String email,
        String nombre,
        String motivo,
        Instant timestamp
) {}
