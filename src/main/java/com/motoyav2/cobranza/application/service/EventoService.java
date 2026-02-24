package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoCobranzaPort eventoPort;

    // -------------------------------------------------------------------------
    // Listar eventos de un contrato ordenados por fecha desc
    // -------------------------------------------------------------------------

    public Flux<EventoCobranzaDocument> listar(String contratoId) {
        return eventoPort.findByContratoId(contratoId)
                .sort(Comparator.comparing(
                        EventoCobranzaDocument::getCreadoEn,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ));
    }

    // -------------------------------------------------------------------------
    // Crear evento manual
    // -------------------------------------------------------------------------

    public Mono<EventoCobranzaDocument> crearManual(String contratoId, String tipo,
                                                      Map<String, Object> payload,
                                                      String usuarioId, String usuarioNombre) {
        EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(contratoId)
                .tipo(tipo)
                .payload(payload)
                .usuarioId(usuarioId)
                .usuarioNombre(usuarioNombre)
                .automatico(false)
                .creadoEn(new Date())
                .build();

        return eventoPort.append(contratoId, evento);
    }
}
