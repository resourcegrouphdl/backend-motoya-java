package com.motoyav2.cobranza.application.port.in.command;

public record CerrarPromesaCommand(
        String contratoId,
        String promesaId,
        /** EstadoPromesa: CUMPLIDA | INCUMPLIDA | CANCELADA */
        String resultado,
        /** Solo si resultado == CUMPLIDA */
        Double montoPagado,
        /** Solo si resultado == INCUMPLIDA | CANCELADA */
        String motivo,
        String usuarioId,
        String usuarioNombre
) {}
