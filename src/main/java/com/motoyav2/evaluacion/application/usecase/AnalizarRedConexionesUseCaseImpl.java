package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.model.*;
import com.motoyav2.evaluacion.domain.port.in.AnalizarRedConexionesUseCase;
import com.motoyav2.evaluacion.domain.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalizarRedConexionesUseCaseImpl implements AnalizarRedConexionesUseCase {

    private static final int MAX_POR_TELEFONO = 10;
    private static final String ORIGEN_LISTA_NEGRA = "Lista Negra Interna";

    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;
    private final SolicitudRepository solicitudRepository;
    private final ReferenciaRepository referenciaRepository;
    private final RegistroRiesgoRepository registroRiesgoRepository;

    @Override
    public Mono<List<HallazgoRedConexiones>> analizar(String solicitudId) {
        return obtenerExpedienteUseCase.ejecutar(solicitudId)
                .flatMap(expediente -> {
                    List<PhoneEntry> phones = recopilarTelefonos(expediente);

                    // Análisis histórico (conexiones entre solicitudes)
                    Mono<List<HallazgoRedConexiones>> historico = phones.isEmpty()
                            ? Mono.just(List.of())
                            : Flux.fromIterable(phones)
                                    .flatMap(entry -> analizarTelefono(entry, solicitudId), 4)
                                    .filter(h -> !h.getSolicitudesRelacionadas().isEmpty())
                                    .collectList();

                    // Análisis de lista negra (teléfonos + DNIs)
                    Mono<List<HallazgoRedConexiones>> listaNegra = Mono.zip(
                            analizarListaNegraPorTelefonos(phones),
                            analizarListaNegraPorDni(expediente)
                    ).map(t -> {
                        List<HallazgoRedConexiones> combined = new ArrayList<>(t.getT1());
                        combined.addAll(t.getT2());
                        return combined;
                    });

                    return Mono.zip(historico, listaNegra)
                            .map(t -> {
                                List<HallazgoRedConexiones> todos = new ArrayList<>(t.getT2()); // lista negra primero
                                todos.addAll(t.getT1());
                                todos.sort(Comparator.comparingInt(h -> ordenSeveridad(h.getSeveridad())));
                                return todos;
                            });
                })
                .onErrorResume(e -> {
                    log.error("[RED-CONEXIONES] Error analizando solicitud={}: {}", solicitudId, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    // ── Lista negra — por teléfono ────────────────────────────────────────────

    private Mono<List<HallazgoRedConexiones>> analizarListaNegraPorTelefonos(List<PhoneEntry> phones) {
        if (phones.isEmpty()) return Mono.just(List.of());
        return Flux.fromIterable(phones)
                .flatMap(entry -> registroRiesgoRepository.findByTelefono(entry.digitos())
                        .filter(r -> "ACTIVO".equals(r.getEstadoRegistro() != null ? r.getEstadoRegistro().name() : ""))
                        .next()
                        .map(r -> HallazgoRedConexiones.builder()
                                .telefono(entry.digitos())
                                .rolEnExpediente(entry.rol())
                                .severidad("CRITICA")
                                .descripcion(String.format("%s (%s) figura en la lista negra interna — %s",
                                        entry.digitos(), entry.rol(), r.getNombreRegistrado()))
                                .solicitudesRelacionadas(List.of())
                                .origen(ORIGEN_LISTA_NEGRA)
                                .build())
                )
                .collectList();
    }

    // ── Lista negra — por DNI ─────────────────────────────────────────────────

    private Mono<List<HallazgoRedConexiones>> analizarListaNegraPorDni(Expediente expediente) {
        List<Mono<Optional<HallazgoRedConexiones>>> checks = new ArrayList<>();

        String dniTitular = expediente.getTitular() != null ? expediente.getTitular().getDocumentNumber() : null;
        if (dniTitular != null && !dniTitular.isBlank()) {
            checks.add(checkDni(dniTitular, "Titular"));
        }

        Cliente fiador = expediente.getFiador();
        String dniFiador = fiador != null ? fiador.getDocumentNumber() : null;
        if (dniFiador != null && !dniFiador.isBlank() && !dniFiador.equals(dniTitular)) {
            checks.add(checkDni(dniFiador, "Aval"));
        }

        if (checks.isEmpty()) return Mono.just(List.of());

        return Flux.fromIterable(checks)
                .flatMap(m -> m)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collectList();
    }

    private Mono<Optional<HallazgoRedConexiones>> checkDni(String dni, String rol) {
        return registroRiesgoRepository.findByDni(dni)
                .filter(r -> "ACTIVO".equals(r.getEstadoRegistro() != null ? r.getEstadoRegistro().name() : ""))
                .next()
                .map(r -> Optional.of(HallazgoRedConexiones.builder()
                        .telefono(null)
                        .rolEnExpediente(rol + " (DNI)")
                        .severidad("CRITICA")
                        .descripcion(String.format("DNI %s (%s) figura en la lista negra interna — %s",
                                dni, rol, r.getNombreRegistrado()))
                        .solicitudesRelacionadas(List.of())
                        .origen(ORIGEN_LISTA_NEGRA)
                        .build()))
                .defaultIfEmpty(Optional.empty());
    }

    // ── Análisis histórico (conexiones entre solicitudes) ─────────────────────

    private List<PhoneEntry> recopilarTelefonos(Expediente expediente) {
        Map<String, PhoneEntry> unicos = new LinkedHashMap<>();

        Cliente titular = expediente.getTitular();
        agregarSiPresente(unicos, titular.getTelefono1(), "Titular");
        agregarSiPresente(unicos, titular.getTelefono2(), "Titular (2°)");

        Cliente fiador = expediente.getFiador();
        if (fiador != null) {
            agregarSiPresente(unicos, fiador.getTelefono1(), "Aval");
            agregarSiPresente(unicos, fiador.getTelefono2(), "Aval (2°)");
        }

        List<Referencia> refs = expediente.getReferencias();
        if (refs != null) {
            for (Referencia ref : refs) {
                agregarSiPresente(unicos, ref.getTelefono(), "Referencia " + ref.getNumero());
            }
        }

        return new ArrayList<>(unicos.values());
    }

    private void agregarSiPresente(Map<String, PhoneEntry> map, String telefono, String rol) {
        if (telefono == null || telefono.isBlank()) return;
        String limpio = soloDigitos(telefono);
        if (limpio.length() >= 9) map.putIfAbsent(limpio, new PhoneEntry(telefono, limpio, rol));
    }

    private Mono<HallazgoRedConexiones> analizarTelefono(PhoneEntry entry, String solicitudIdActual) {
        Flux<HallazgoRedConexiones.SolicitudReferenciada> porTitular =
                solicitudRepository.findByTitularTelefono(entry.raw(), MAX_POR_TELEFONO)
                        .filter(s -> !solicitudIdActual.equals(s.getId()))
                        .map(s -> toRef(s, "titular en otra solicitud"));

        Flux<HallazgoRedConexiones.SolicitudReferenciada> porFiador =
                solicitudRepository.findByFiadorTelefono(entry.raw(), MAX_POR_TELEFONO)
                        .filter(s -> !solicitudIdActual.equals(s.getId()))
                        .map(s -> toRef(s, "aval en otra solicitud"));

        Flux<HallazgoRedConexiones.SolicitudReferenciada> porReferencia =
                referenciaRepository.findByTelefono(entry.raw(), MAX_POR_TELEFONO)
                        .filter(r -> r.getSolicitudId() != null && !solicitudIdActual.equals(r.getSolicitudId()))
                        .map(this::toRefDesdeReferencia);

        return Flux.merge(porTitular, porFiador, porReferencia)
                .distinct(HallazgoRedConexiones.SolicitudReferenciada::getSolicitudId)
                .collectList()
                .map(relacionadas -> HallazgoRedConexiones.builder()
                        .telefono(entry.digitos())
                        .rolEnExpediente(entry.rol())
                        .severidad(severidad(relacionadas.size()))
                        .descripcion(descripcion(entry.digitos(), entry.rol(), relacionadas))
                        .solicitudesRelacionadas(relacionadas)
                        .build());
    }

    private HallazgoRedConexiones.SolicitudReferenciada toRef(Solicitud s, String rol) {
        String codigo = s.getCodigoDeSolicitud() != null ? s.getCodigoDeSolicitud() : s.getNumeroSolicitud();
        String estado = s.getEstado() != null ? s.getEstado().getFirestoreValue() : "desconocido";
        return HallazgoRedConexiones.SolicitudReferenciada.builder()
                .solicitudId(s.getId())
                .codigoSolicitud(codigo)
                .rolEncontrado(rol)
                .estado(estado)
                .build();
    }

    private HallazgoRedConexiones.SolicitudReferenciada toRefDesdeReferencia(Referencia r) {
        String parentesco = r.getParentesco() != null ? r.getParentesco() : "—";
        String estado = r.getEstadoVerificacion() != null ? r.getEstadoVerificacion() : "desconocido";
        return HallazgoRedConexiones.SolicitudReferenciada.builder()
                .solicitudId(r.getSolicitudId())
                .codigoSolicitud(r.getSolicitudId().substring(0, Math.min(8, r.getSolicitudId().length())) + "…")
                .rolEncontrado("referencia (" + parentesco + ")")
                .estado(estado)
                .build();
    }

    private String severidad(int n) {
        if (n >= 3) return "CRITICA";
        if (n >= 1) return "ALTA";
        return "MEDIA";
    }

    private String descripcion(String digitos, String rol, List<HallazgoRedConexiones.SolicitudReferenciada> relacionadas) {
        int n = relacionadas.size();
        return String.format("%s (%s) aparece en %d solicitud%s previa%s",
                digitos, rol, n,
                n > 1 ? "es" : "",
                n > 1 ? "s" : "");
    }

    private int ordenSeveridad(String sev) {
        return switch (sev) {
            case "CRITICA" -> 0;
            case "ALTA"    -> 1;
            default        -> 2;
        };
    }

    private String soloDigitos(String phone) {
        String d = phone.replaceAll("[^0-9]", "");
        if (d.startsWith("51") && d.length() == 11) d = d.substring(2);
        return d;
    }

    record PhoneEntry(String raw, String digitos, String rol) {}
}
