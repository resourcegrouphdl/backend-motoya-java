package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Domain Service puro — sin I/O.
 * Implementa la máquina de estados del proceso de evaluación de crédito.
 *
 * Patrón: State Machine con tabla de transiciones permitidas.
 * Cada estado origen tiene un Set de estados destino válidos.
 * La validación evita transiciones ilegales independientemente del frontend.
 */
@Component
public class MotorDePipeline {

    /**
     * Tabla de transiciones permitidas.
     * Key = estado actual.  Value = estados a los que puede transicionar.
     */
    private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> TRANSICIONES = Map.ofEntries(

        Map.entry(EstadoSolicitud.pendiente, Set.of(
                EstadoSolicitud.en_revision_inicial,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.en_revision_inicial, Set.of(
                EstadoSolicitud.evaluacion_documental,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.evaluacion_documental, Set.of(
                EstadoSolicitud.documentos_completos,
                EstadoSolicitud.documentos_observados,
                EstadoSolicitud.documentos_incompletos,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.documentos_observados, Set.of(
                EstadoSolicitud.evaluacion_documental,  // el cliente resubmite
                EstadoSolicitud.documentos_completos,
                EstadoSolicitud.documentos_incompletos,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.documentos_incompletos, Set.of(
                EstadoSolicitud.evaluacion_documental,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.documentos_completos, Set.of(
                EstadoSolicitud.cliente_aprobado,
                EstadoSolicitud.cliente_rechazado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.cliente_aprobado, Set.of(
                EstadoSolicitud.evaluacion_garantes,
                EstadoSolicitud.referencias_aprobadas,  // sin fiador → saltar garantes
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.cliente_rechazado, Set.of(
                EstadoSolicitud.rechazado,
                EstadoSolicitud.evaluacion_documental,  // revisión adicional
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.evaluacion_garantes, Set.of(
                EstadoSolicitud.fiador_aprobado,
                EstadoSolicitud.fiador_rechazado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.fiador_aprobado, Set.of(
                EstadoSolicitud.referencias_aprobadas,
                EstadoSolicitud.referencias_rechazadas,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.fiador_rechazado, Set.of(
                EstadoSolicitud.rechazado,
                EstadoSolicitud.evaluacion_garantes,    // nuevo fiador
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.referencias_aprobadas, Set.of(
                EstadoSolicitud.vehiculo_aprobado,
                EstadoSolicitud.vehiculo_rechazado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.referencias_rechazadas, Set.of(
                EstadoSolicitud.rechazado,
                EstadoSolicitud.referencias_aprobadas,  // revisión
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.vehiculo_aprobado, Set.of(
                EstadoSolicitud.datos_verificados,
                EstadoSolicitud.datos_no_verificados,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.vehiculo_rechazado, Set.of(
                EstadoSolicitud.rechazado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.datos_verificados, Set.of(
                EstadoSolicitud.entrevista_programada,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.datos_no_verificados, Set.of(
                EstadoSolicitud.datos_verificados,
                EstadoSolicitud.rechazado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.entrevista_programada, Set.of(
                EstadoSolicitud.entrevista_en_curso,
                EstadoSolicitud.entrevista_programada,  // reprogramar
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.entrevista_en_curso, Set.of(
                EstadoSolicitud.entrevista_completada,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.entrevista_completada, Set.of(
                EstadoSolicitud.en_revision_final,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.en_revision_final, Set.of(
                EstadoSolicitud.aprobado,
                EstadoSolicitud.rechazado,
                EstadoSolicitud.condicional,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.aprobado, Set.of(
                EstadoSolicitud.certificado_generado
        )),
        Map.entry(EstadoSolicitud.condicional, Set.of(
                EstadoSolicitud.en_revision_final,
                EstadoSolicitud.aprobado,
                EstadoSolicitud.rechazado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.certificado_generado, Set.of(
                EstadoSolicitud.esperando_inicial
        )),
        Map.entry(EstadoSolicitud.esperando_inicial, Set.of(
                EstadoSolicitud.contrato_generado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.contrato_generado, Set.of(
                EstadoSolicitud.contrato_firmado,
                EstadoSolicitud.cancelado
        )),
        Map.entry(EstadoSolicitud.contrato_firmado, Set.of(
                EstadoSolicitud.entrega_completada,
                EstadoSolicitud.cancelado
        )),
        // Estados terminales — sin transiciones adicionales
        Map.entry(EstadoSolicitud.rechazado,          Set.of()),
        Map.entry(EstadoSolicitud.entrega_completada, Set.of()),
        Map.entry(EstadoSolicitud.cancelado,          Set.of())
    );

    /**
     * Valida si la transición es permitida.
     * @throws IllegalStateException si la transición no está permitida
     */
    public void validarTransicion(EstadoSolicitud estadoActual, EstadoSolicitud estadoNuevo) {
        if (estadoActual == null) {
            throw new IllegalStateException("El estado actual no puede ser null");
        }
        if (estadoNuevo == null) {
            throw new IllegalStateException("El estado destino no puede ser null");
        }
        if (estadoActual == estadoNuevo) {
            // Solo permitir auto-transición en reprogramación de entrevista
            if (estadoActual != EstadoSolicitud.entrevista_programada) {
                throw new IllegalStateException(
                        "Transición al mismo estado no permitida: " + estadoActual);
            }
        }

        Set<EstadoSolicitud> permitidos = TRANSICIONES.get(estadoActual);
        if (permitidos == null || !permitidos.contains(estadoNuevo)) {
            throw new IllegalStateException(String.format(
                    "Transición no permitida: %s → %s. Estados válidos desde '%s': %s",
                    estadoActual, estadoNuevo, estadoActual,
                    permitidos != null ? permitidos : "ninguno (estado terminal)"));
        }
    }

    /**
     * Retorna si una transición es válida sin lanzar excepción.
     */
    public boolean esTransicionValida(EstadoSolicitud desde, EstadoSolicitud hacia) {
        Set<EstadoSolicitud> permitidos = TRANSICIONES.get(desde);
        return permitidos != null && permitidos.contains(hacia);
    }

    /**
     * Retorna los estados válidos desde el estado actual.
     */
    public Set<EstadoSolicitud> transicionesPosibles(EstadoSolicitud desde) {
        return TRANSICIONES.getOrDefault(desde, Set.of());
    }
}
