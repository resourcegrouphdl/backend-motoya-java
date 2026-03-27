package com.motoyav2.alertascenter.domain.model;

import java.util.Map;

public record AlertaDatosEnriquecidos(
        String titulo,
        String mensaje,
        Map<String, Object> payload,
        String fuenteColeccion
) {}
