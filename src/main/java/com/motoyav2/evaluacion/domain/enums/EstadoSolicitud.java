package com.motoyav2.evaluacion.domain.enums;

/**
 * Los 30 estados del ciclo de vida de una solicitud de crédito.
 * Contrato TypeScript: EstadoSolicitud.
 * IMPORTANTE: los values en minúsculas con guiones bajos deben coincidir
 * EXACTAMENTE con los valores guardados en Firestore (colección solicitudes).
 */
public enum EstadoSolicitud {

    pendiente,
    en_revision_inicial,
    evaluacion_documental,
    documentos_observados,
    documentos_completos,
    documentos_incompletos,
    cliente_aprobado,
    cliente_rechazado,
    evaluacion_garantes,
    fiador_aprobado,
    fiador_rechazado,
    referencias_aprobadas,
    referencias_rechazadas,
    vehiculo_aprobado,
    vehiculo_rechazado,
    datos_verificados,
    datos_no_verificados,
    entrevista_programada,
    entrevista_en_curso,
    entrevista_completada,
    en_revision_final,
    aprobado,
    rechazado,
    condicional,
    certificado_generado,
    esperando_inicial,
    contrato_generado,
    contrato_firmado,
    entrega_completada,
    cancelado;

    /** Parsea desde string Firestore (tolerante a null). */
    public static EstadoSolicitud fromString(String valor) {
        if (valor == null || valor.isBlank()) return pendiente;
        try {
            return EstadoSolicitud.valueOf(valor.trim().toLowerCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return pendiente;
        }
    }

    public boolean esTerminal() {
        return this == aprobado || this == rechazado || this == entrega_completada || this == cancelado;
    }
}
