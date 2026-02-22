package com.motoyav2.contrato.domain.model;

import java.time.Instant;

public record Notificacion(
        String tipo,
        String mensaje,
        String destinatario,
        Instant fecha,
        Boolean exitoso
) {
}
