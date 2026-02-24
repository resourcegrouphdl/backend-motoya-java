package com.motoyav2.cobranza.application.port.in.command;

import java.util.Map;

public record EnviarMensajeWhatsappCommand(
        String contratoId,
        String plantillaId,
        /** Variables para reemplazar en el template {{variable}} */
        Map<String, String> variables,
        String agenteId,
        String agenteNombre,
        String storeId,
        String telefono
) {}
