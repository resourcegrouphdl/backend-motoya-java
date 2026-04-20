package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;

/**
 * Resultado de la validación de email (sintaxis + MX DNS).
 * Se persiste en Firestore como campo {@code validacionEmail} dentro del documento de cliente.
 */
public record ValidacionEmail(
        boolean valido,
        String  nivel,        // MX_OK | SINTAXIS_INVALIDA | DOMINIO_SIN_MX | DOMINIO_NO_ENCONTRADO | EMAIL_VACIO
        String  detalle,
        Timestamp verificadoEn
) {}
