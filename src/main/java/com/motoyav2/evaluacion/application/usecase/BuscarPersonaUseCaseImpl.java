package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.AlertaCredito;
import com.motoyav2.evaluacion.domain.model.PersonaResumen;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.in.BuscarPersonaUseCase;
import com.motoyav2.evaluacion.domain.port.out.PersonaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementa la central de riesgo interna.
 * Busca el historial de una persona y construye las alertas correspondientes
 * para que el vendedor las vea ANTES de enviar la solicitud.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuscarPersonaUseCaseImpl implements BuscarPersonaUseCase {

    /** Máximo de solicitudes a revisar por dirección de búsqueda (titular / fiador). */
    private static final int MAX_HISTORIAL = 10;

    /**
     * Estados que representan una solicitud "activa" (en proceso de evaluación).
     */
    private static final Set<EstadoSolicitud> ESTADOS_ACTIVOS = Set.of(
            EstadoSolicitud.PENDIENTE,
            EstadoSolicitud.EN_REVISION_INICIAL,
            EstadoSolicitud.EVALUACION_DOCUMENTAL,
            EstadoSolicitud.DOCUMENTOS_OBSERVADOS,
            EstadoSolicitud.DOCUMENTOS_COMPLETOS,
            EstadoSolicitud.DOCUMENTOS_INCOMPLETOS,
            EstadoSolicitud.CLIENTE_APROBADO,
            EstadoSolicitud.EVALUACION_GARANTES,
            EstadoSolicitud.FIADOR_APROBADO,
            EstadoSolicitud.FIADOR_RECHAZADO,
            EstadoSolicitud.REFERENCIAS_APROBADAS,
            EstadoSolicitud.REFERENCIAS_RECHAZADAS,
            EstadoSolicitud.VEHICULO_APROBADO,
            EstadoSolicitud.DATOS_VERIFICADOS,
            EstadoSolicitud.ENTREVISTA_PROGRAMADA,
            EstadoSolicitud.ENTREVISTA_EN_CURSO,
            EstadoSolicitud.ENTREVISTA_COMPLETADA,
            EstadoSolicitud.EN_REVISION_FINAL,
            EstadoSolicitud.CONDICIONAL,
            EstadoSolicitud.CERTIFICADO_GENERADO,
            EstadoSolicitud.ESPERANDO_INICIAL,
            EstadoSolicitud.CONTRATO_GENERADO
    );

    private static final Set<EstadoSolicitud> ESTADOS_RECHAZADO = Set.of(
            EstadoSolicitud.RECHAZADO,
            EstadoSolicitud.CLIENTE_RECHAZADO,
            EstadoSolicitud.CANCELADO
    );

    private final PersonaRepository personaRepository;
    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<PersonaResumen> ejecutar(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return Mono.empty();
        }

        String dni = documentNumber.trim();

        // Ejecutar en paralelo: datos de persona + historial como titular + historial como fiador
        Mono<Map<String, Object>> personaMono =
                personaRepository.findByDocumentNumber(dni).defaultIfEmpty(Map.of());

        Mono<List<Solicitud>> comoTitularMono =
                solicitudRepository.findByTitularDni(dni, MAX_HISTORIAL).collectList();

        Mono<List<Solicitud>> comoFiadorMono =
                solicitudRepository.findByFiadorDni(dni, MAX_HISTORIAL).collectList();

        return Mono.zip(personaMono, comoTitularMono, comoFiadorMono)
                .map(tuple -> {
                    Map<String, Object> persona = tuple.getT1();
                    List<Solicitud> comoTitular = tuple.getT2();
                    List<Solicitud> comoFiador  = tuple.getT3();

                    List<AlertaCredito> alertas = construirAlertas(comoTitular, comoFiador);

                    return PersonaResumen.builder()
                            .documentNumber(dni)
                            .documentType(str(persona, "documentType"))
                            .nombres(str(persona, "nombres"))
                            .apellidoPaterno(str(persona, "apellidoPaterno"))
                            .apellidoMaterno(str(persona, "apellidoMaterno"))
                            .email(str(persona, "email"))
                            .telefono1(str(persona, "telefono1"))
                            .telefono2(str(persona, "telefono2"))
                            .estadoCivil(str(persona, "estadoCivil"))
                            .fechaNacimiento(str(persona, "fechaNacimiento"))
                            .departamento(str(persona, "departamento"))
                            .provincia(str(persona, "provincia"))
                            .distrito(str(persona, "distrito"))
                            .direccion(str(persona, "direccion"))
                            .ocupacion(str(persona, "ocupacion"))
                            .rangoIngresos(str(persona, "rangoIngresos"))
                            .tipoVivienda(str(persona, "tipoVivienda"))
                            .licenciaConducir(str(persona, "licenciaConducir"))
                            .numeroLicencia(str(persona, "numeroLicencia"))
                            .alertas(alertas)
                            .build();
                })
                .doOnSuccess(r -> log.debug("[BUSCAR-PERSONA] dni={} alertas={}", dni, r.getAlertas().size()));
    }

    // ── Motor de alertas ──────────────────────────────────────────────────────

    private List<AlertaCredito> construirAlertas(List<Solicitud> comoTitular,
                                                  List<Solicitud> comoFiador) {
        List<AlertaCredito> alertas = new ArrayList<>();

        // 1. Solicitudes activas como TITULAR
        comoTitular.stream()
                .filter(s -> ESTADOS_ACTIVOS.contains(s.getEstado()))
                .forEach(s -> alertas.add(AlertaCredito.builder()
                        .nivel(AlertaCredito.Nivel.BLOQUEANTE)
                        .tipo(AlertaCredito.Tipo.SOLICITUD_ACTIVA_TITULAR)
                        .descripcion("Esta persona tiene una solicitud activa como TITULAR (estado: "
                                + s.getEstado().getFirestoreValue() + ")")
                        .codigoSolicitudRelacionada(s.getCodigoDeSolicitud())
                        .estadoSolicitudRelacionada(s.getEstado().getFirestoreValue())
                        .build()));

        // 2. Solicitudes RECHAZADAS como titular
        comoTitular.stream()
                .filter(s -> ESTADOS_RECHAZADO.contains(s.getEstado()))
                .forEach(s -> alertas.add(AlertaCredito.builder()
                        .nivel(AlertaCredito.Nivel.ADVERTENCIA)
                        .tipo(AlertaCredito.Tipo.SOLICITUD_RECHAZADA)
                        .descripcion("Esta persona tuvo una solicitud RECHAZADA como titular")
                        .codigoSolicitudRelacionada(s.getCodigoDeSolicitud())
                        .estadoSolicitudRelacionada(s.getEstado().getFirestoreValue())
                        .motivoRechazo(s.getMotivoRechazo())
                        .build()));

        // 3. Solicitudes activas como FIADOR
        long fiadoresActivos = comoFiador.stream()
                .filter(s -> ESTADOS_ACTIVOS.contains(s.getEstado()))
                .count();

        if (fiadoresActivos > 0) {
            // Reportar cada una individualmente
            comoFiador.stream()
                    .filter(s -> ESTADOS_ACTIVOS.contains(s.getEstado()))
                    .forEach(s -> alertas.add(AlertaCredito.builder()
                            .nivel(AlertaCredito.Nivel.ADVERTENCIA)
                            .tipo(AlertaCredito.Tipo.SOLICITUD_ACTIVA_FIADOR)
                            .descripcion("Esta persona ya es FIADOR en otra solicitud activa")
                            .codigoSolicitudRelacionada(s.getCodigoDeSolicitud())
                            .estadoSolicitudRelacionada(s.getEstado().getFirestoreValue())
                            .build()));
        }

        // 4. Fiador sobrecargado (≥3 solicitudes activas como fiador)
        if (fiadoresActivos >= 3) {
            alertas.add(AlertaCredito.builder()
                    .nivel(AlertaCredito.Nivel.BLOQUEANTE)
                    .tipo(AlertaCredito.Tipo.FIADOR_SOBRECARGADO)
                    .descripcion("Esta persona ya es fiador en " + fiadoresActivos
                            + " solicitudes activas. No puede asumir más garantías.")
                    .build());
        }

        // 5. Rol anterior distinto (fue titular antes, ahora se registra como fiador o viceversa)
        boolean fueAntesTitular = !comoTitular.isEmpty();
        boolean fueAntesFiador  = !comoFiador.isEmpty();

        if (fueAntesTitular && fueAntesFiador) {
            alertas.add(AlertaCredito.builder()
                    .nivel(AlertaCredito.Nivel.ADVERTENCIA)
                    .tipo(AlertaCredito.Tipo.ROL_ANTERIOR_DISTINTO)
                    .descripcion("Esta persona ha participado como TITULAR y como FIADOR en solicitudes anteriores. "
                            + "Verifique que no existan relaciones cruzadas entre las solicitudes.")
                    .build());
        } else if (fueAntesTitular) {
            alertas.add(AlertaCredito.builder()
                    .nivel(AlertaCredito.Nivel.ADVERTENCIA)
                    .tipo(AlertaCredito.Tipo.ROL_ANTERIOR_DISTINTO)
                    .descripcion("Esta persona ha participado anteriormente como TITULAR en "
                            + comoTitular.size() + " solicitud(es).")
                    .build());
        } else if (fueAntesFiador) {
            alertas.add(AlertaCredito.builder()
                    .nivel(AlertaCredito.Nivel.ADVERTENCIA)
                    .tipo(AlertaCredito.Tipo.ROL_ANTERIOR_DISTINTO)
                    .descripcion("Esta persona ha participado anteriormente como FIADOR en "
                            + comoFiador.size() + " solicitud(es).")
                    .build());
        }

        return alertas;
    }

    private static String str(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
