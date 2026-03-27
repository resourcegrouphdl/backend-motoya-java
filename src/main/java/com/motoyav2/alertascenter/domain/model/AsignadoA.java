package com.motoyav2.alertascenter.domain.model;

import java.time.Instant;

public record AsignadoA(
        String userId,
        String email,
        String nombre,
        Instant fechaAsignacion
) {}
