package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.in.DetectarDuplicadosUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectarDuplicadosUseCaseImpl implements DetectarDuplicadosUseCase {

    private static final Set<EstadoSolicitud> ESTADOS_TERMINALES = Set.of(
            EstadoSolicitud.RECHAZADO,
            EstadoSolicitud.CANCELADO,
            EstadoSolicitud.ARCHIVADA,
            EstadoSolicitud.ENTREGA_COMPLETADA
    );

    private static final int MAX_HISTORIAL = 5;

    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<Void> detectar(String solicitudId, String titularDni, String fiadorDni) {
        if (titularDni == null && fiadorDni == null) return Mono.empty();

        Flux<Solicitud> porTitular = titularDni != null
                ? solicitudRepository.findByTitularDni(titularDni, MAX_HISTORIAL)
                        .filter(s -> !solicitudId.equals(s.getId()))
                : Flux.empty();

        Flux<Solicitud> porFiador = fiadorDni != null
                ? Flux.merge(
                        solicitudRepository.findByTitularDni(fiadorDni, MAX_HISTORIAL),
                        solicitudRepository.findByFiadorDni(fiadorDni, MAX_HISTORIAL))
                        .filter(s -> !solicitudId.equals(s.getId()))
                : Flux.empty();

        return Flux.merge(porTitular.map(s -> Map.entry("titular", s)),
                          porFiador.map(s -> Map.entry("fiador", s)))
                .collectList()
                .flatMap(entradas -> {
                    if (entradas.isEmpty()) return Mono.empty();

                    List<String> relacionadas = new ArrayList<>();
                    List<String> descripciones = new ArrayList<>();
                    boolean tieneActiva = false;

                    for (var entrada : entradas) {
                        Solicitud s = entrada.getValue();
                        String rol = entrada.getKey();
                        relacionadas.add(s.getId());

                        boolean activa = s.getEstado() == null || !ESTADOS_TERMINALES.contains(s.getEstado());
                        if (activa) tieneActiva = true;

                        String estado = s.getEstado() != null ? s.getEstado().getFirestoreValue() : "desconocido";
                        String codigo = s.getCodigoDeSolicitud() != null ? s.getCodigoDeSolicitud() : s.getId();
                        descripciones.add(String.format("%s aparece como %s en solicitud %s (%s)",
                                "titular".equals(rol) ? titularDni : fiadorDni, rol, codigo, estado));
                    }

                    Map<String, Object> alerta = new HashMap<>();
                    alerta.put("tieneDuplicado", true);
                    alerta.put("tieneActiva", tieneActiva);
                    alerta.put("descripciones", descripciones);
                    alerta.put("solicitudesRelacionadas", relacionadas);
                    alerta.put("detectadoEn", Timestamp.now());

                    log.warn("[DUPLICADOS] solicitud={} duplicados encontrados={} activos={}",
                            solicitudId, relacionadas.size(), tieneActiva);

                    return solicitudRepository.updateFields(solicitudId, Map.of(
                            "alertaDuplicado", alerta,
                            "updatedAt", Timestamp.now()
                    ));
                })
                .onErrorResume(e -> {
                    log.error("[DUPLICADOS] Error detectando duplicados solicitud={}: {}", solicitudId, e.getMessage());
                    return Mono.empty();
                });
    }
}
