package com.motoyav2.cobranza.application.port.in.command;

public record AsignarAgenteCommand(
        String contratoId,
        String agenteAnteriorId,
        String agenteNuevoId,
        String agenteNuevoNombre,
        String motivo,
        String supervisorId,
        String supervisorNombre
) {}
