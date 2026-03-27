package com.motoyav2.alertascenter.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AlertaInterna(
        String id,
        TipoAlerta tipo,
        SubTipoAlerta subTipo,
        String titulo,
        String mensaje,
        Map<String, Object> payload,
        EstadoAlerta estado,
        AsignadoA asignadoA,
        List<DeclineEntry> declines,
        String fuenteId,
        String fuenteColeccion,
        Instant creadoEn,
        Instant actualizadoEn
) {}
