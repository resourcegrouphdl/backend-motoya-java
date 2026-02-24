package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.EstrategiaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EstrategiaService {

    private final EstrategiaPort estrategiaPort;
    private final EventoCobranzaPort eventoPort;

    // -------------------------------------------------------------------------
    // Listar estrategias
    // -------------------------------------------------------------------------

    public Flux<EstrategiaDocument> listar() {
        return estrategiaPort.findAll();
    }

    // -------------------------------------------------------------------------
    // Actualizar campos editables de una estrategia
    // -------------------------------------------------------------------------

    public Mono<EstrategiaDocument> actualizar(String id, Boolean activo, String mensaje,
                                                Integer frecuenciaDias, String usuarioId) {
        return estrategiaPort.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Estrategia no encontrada: " + id)))
                .flatMap(estrategia -> {
                    if (activo != null) {
                        estrategia.setActivo(activo);
                    }
                    if (mensaje != null) {
                        estrategia.setMensaje(mensaje);
                    }
                    if (frecuenciaDias != null) {
                        estrategia.setFrecuenciaDias(frecuenciaDias);
                    }
                    estrategia.setActualizadoEn(new Date());
                    return estrategiaPort.save(estrategia);
                });
    }

    // -------------------------------------------------------------------------
    // Disparar estrategia manualmente para una lista de contratos
    // -------------------------------------------------------------------------

    public Mono<Void> dispararManual(String estrategiaId, List<String> contratoIds,
                                      String observaciones, String usuarioId, String usuarioNombre) {
        return estrategiaPort.findById(estrategiaId)
                .switchIfEmpty(Mono.error(new NotFoundException("Estrategia no encontrada: " + estrategiaId)))
                .flatMapMany(estrategia -> Flux.fromIterable(contratoIds)
                        .flatMap(contratoId -> {
                            EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                                    .id(UUID.randomUUID().toString())
                                    .contratoId(contratoId)
                                    .tipo("ESTRATEGIA_DISPARADA")
                                    .payload(Map.of(
                                            "estrategiaId", estrategiaId,
                                            "observaciones", observaciones != null ? observaciones : "",
                                            "manual", true
                                    ))
                                    .usuarioId(usuarioId)
                                    .usuarioNombre(usuarioNombre)
                                    .automatico(false)
                                    .creadoEn(new Date())
                                    .build();

                            return eventoPort.append(contratoId, evento);
                        })
                )
                .then();
    }
}
