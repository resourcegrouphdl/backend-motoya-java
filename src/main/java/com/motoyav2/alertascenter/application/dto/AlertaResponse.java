package com.motoyav2.alertascenter.application.dto;

import com.motoyav2.alertascenter.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AlertaResponse(
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
) {
    public static AlertaResponse from(AlertaInterna alerta) {
        return new AlertaResponse(
                alerta.id(),
                alerta.tipo(),
                alerta.subTipo(),
                alerta.titulo(),
                alerta.mensaje(),
                alerta.payload(),
                alerta.estado(),
                alerta.asignadoA(),
                alerta.declines(),
                alerta.fuenteId(),
                alerta.fuenteColeccion(),
                alerta.creadoEn(),
                alerta.actualizadoEn()
        );
    }
}
