package com.motoyav2.cobranza.application.port.in.command;

public record AprobarVoucherCommand(
        String voucherId,
        String agenteId,
        String agenteNombre,
        /** "B001" (boleta) o "F001" (factura) */
        String serie,
        String rucEmisor,
        String razonSocialEmisor,
        String direccionEmisor,
        /** TipoDocumentoReceptor: DNI | RUC | CE */
        String tipoDocumentoReceptor,
        String numeroDocumentoReceptor,
        String nombreReceptor,
        String descripcionItem
) {}
