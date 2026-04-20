package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * Alerta que se devuelve al vendedor al buscar una persona por documento.
 * Permite implementar la central de riesgo interna.
 */
@Value
@Builder
public class AlertaCredito {

    /**
     * BLOQUEANTE — el vendedor no debería continuar sin escalar.
     * ADVERTENCIA — el vendedor es informado pero puede continuar.
     */
    public enum Nivel { BLOQUEANTE, ADVERTENCIA }

    public enum Tipo {
        MISMO_DNI_TITULAR_FIADOR,
        SOLICITUD_ACTIVA_TITULAR,
        SOLICITUD_ACTIVA_FIADOR,
        SOLICITUD_RECHAZADA,
        FIADOR_SOBRECARGADO,
        ROL_ANTERIOR_DISTINTO
    }

    Nivel nivel;
    Tipo tipo;
    String descripcion;
    /** Código de la solicitud relacionada, si aplica. */
    String codigoSolicitudRelacionada;
    /** Estado de la solicitud relacionada, si aplica. */
    String estadoSolicitudRelacionada;
    /** Motivo de rechazo, si la alerta es SOLICITUD_RECHAZADA. */
    String motivoRechazo;
}
