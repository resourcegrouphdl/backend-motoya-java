package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.exception.TransicionInvalidaException;

import java.util.Map;
import java.util.Set;

import static com.motoyav2.evaluacion.domain.enums.EstadoSolicitud.*;

/**
 * State machine del ciclo de vida de una solicitud.
 * Define qué transiciones son válidas y las valida en tiempo de ejecución.
 */
public final class EstadoSolicitudStateMachine {

    private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> TRANSICIONES = Map.ofEntries(
            Map.entry(PENDIENTE,               Set.of(EN_REVISION_INICIAL, CANCELADO, ARCHIVADA)),
            Map.entry(EN_REVISION_INICIAL,     Set.of(EVALUACION_DOCUMENTAL, CANCELADO, ARCHIVADA)),
            Map.entry(EVALUACION_DOCUMENTAL,   Set.of(DOCUMENTOS_COMPLETOS, DOCUMENTOS_OBSERVADOS, DOCUMENTOS_INCOMPLETOS, CANCELADO, ARCHIVADA)),
            Map.entry(DOCUMENTOS_OBSERVADOS,   Set.of(EVALUACION_DOCUMENTAL, DOCUMENTOS_COMPLETOS, CANCELADO)),
            Map.entry(DOCUMENTOS_INCOMPLETOS,  Set.of(EVALUACION_DOCUMENTAL, CANCELADO)),
            Map.entry(DOCUMENTOS_COMPLETOS,    Set.of(CLIENTE_APROBADO, CLIENTE_RECHAZADO, CANCELADO)),
            Map.entry(CLIENTE_APROBADO,        Set.of(EVALUACION_GARANTES, CANCELADO)),
            Map.entry(CLIENTE_RECHAZADO,       Set.of(CANCELADO)),
            Map.entry(EVALUACION_GARANTES,     Set.of(FIADOR_APROBADO, FIADOR_RECHAZADO, CANCELADO)),
            Map.entry(FIADOR_APROBADO,         Set.of(REFERENCIAS_APROBADAS, REFERENCIAS_RECHAZADAS, CANCELADO)),
            Map.entry(FIADOR_RECHAZADO,        Set.of(EVALUACION_GARANTES, CANCELADO)),
            Map.entry(REFERENCIAS_APROBADAS,   Set.of(VEHICULO_APROBADO, VEHICULO_RECHAZADO, CANCELADO)),
            Map.entry(REFERENCIAS_RECHAZADAS,  Set.of(EVALUACION_GARANTES, CANCELADO)),
            Map.entry(VEHICULO_APROBADO,       Set.of(DATOS_VERIFICADOS, DATOS_NO_VERIFICADOS, CANCELADO)),
            Map.entry(VEHICULO_RECHAZADO,      Set.of(CANCELADO)),
            Map.entry(DATOS_VERIFICADOS,       Set.of(ENTREVISTA_PROGRAMADA, CANCELADO)),
            Map.entry(DATOS_NO_VERIFICADOS,    Set.of(DATOS_VERIFICADOS, CANCELADO)),
            Map.entry(ENTREVISTA_PROGRAMADA,   Set.of(ENTREVISTA_EN_CURSO, CANCELADO)),
            Map.entry(ENTREVISTA_EN_CURSO,     Set.of(ENTREVISTA_COMPLETADA, CANCELADO)),
            Map.entry(ENTREVISTA_COMPLETADA,   Set.of(EN_REVISION_FINAL, CANCELADO)),
            Map.entry(EN_REVISION_FINAL,       Set.of(APROBADO, RECHAZADO, CONDICIONAL, CANCELADO)),
            Map.entry(APROBADO,                Set.of(CERTIFICADO_GENERADO, CANCELADO)),
            Map.entry(CONDICIONAL,             Set.of(EN_REVISION_FINAL, APROBADO, RECHAZADO, CANCELADO)),
            Map.entry(RECHAZADO,               Set.of(ARCHIVADA)),
            Map.entry(CERTIFICADO_GENERADO,    Set.of(ESPERANDO_INICIAL, CANCELADO)),
            Map.entry(ESPERANDO_INICIAL,       Set.of(CONTRATO_GENERADO, CANCELADO)),
            Map.entry(CONTRATO_GENERADO,       Set.of(CONTRATO_FIRMADO, CANCELADO)),
            Map.entry(CONTRATO_FIRMADO,        Set.of(ENTREGA_COMPLETADA, CANCELADO)),
            Map.entry(ENTREGA_COMPLETADA,      Set.of()),
            Map.entry(CANCELADO,               Set.of()),
            Map.entry(ARCHIVADA,               Set.of())
    );

    private EstadoSolicitudStateMachine() {}

    /**
     * Valida la transición. Lanza {@link TransicionInvalidaException} si no es válida.
     */
    public static void validar(EstadoSolicitud desde, EstadoSolicitud hacia) {
        Set<EstadoSolicitud> permitidos = TRANSICIONES.getOrDefault(desde, Set.of());
        if (!permitidos.contains(hacia)) {
            throw new TransicionInvalidaException(desde, hacia);
        }
    }

    public static Set<EstadoSolicitud> transicionesDesde(EstadoSolicitud estado) {
        return TRANSICIONES.getOrDefault(estado, Set.of());
    }

    public static boolean esTransicionValida(EstadoSolicitud desde, EstadoSolicitud hacia) {
        return TRANSICIONES.getOrDefault(desde, Set.of()).contains(hacia);
    }
}
