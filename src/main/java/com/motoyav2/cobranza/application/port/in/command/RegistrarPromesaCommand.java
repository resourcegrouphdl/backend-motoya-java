package com.motoyav2.cobranza.application.port.in.command;

public record RegistrarPromesaCommand(
        String contratoId,
        /** ISO date YYYY-MM-DD */
        String fecha,
        Double monto,
        String observaciones,
        String agenteId,
        String agenteNombre
) {}
