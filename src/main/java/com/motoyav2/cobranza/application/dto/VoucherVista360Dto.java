package com.motoyav2.cobranza.application.dto;

import java.util.Date;

public record VoucherVista360Dto(
        String id,
        String estado,
        String fuente,
        String thumbPath,
        String imagenPath,
        Double montoDetectado,
        String comprobanteId,
        Date creadoEn,
        String mediaType
) {}
