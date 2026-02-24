package com.motoyav2.cobranza.application.port.in.command;

public record RechazarVoucherCommand(
        String voucherId,
        /** MotivoRechazoVoucher: MONTO_INCORRECTO | IMAGEN_ILEGIBLE | DUPLICADO | DATOS_NO_COINCIDEN | OTRO */
        String motivoRechazo,
        String observaciones,
        String agenteId,
        String agenteNombre
) {}
