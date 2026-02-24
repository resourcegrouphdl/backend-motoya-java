package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.ComprobantePagoPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.ComprobantePagoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobantesService {

    private final ComprobantePagoPort comprobantePagoPort;
    private final EventoCobranzaPort eventoPort;

    // -------------------------------------------------------------------------
    // Listar comprobantes con filtros opcionales en memoria
    // -------------------------------------------------------------------------

    public Flux<ComprobantePagoDocument> listar(String storeId, String contratoId,
                                                 String tipo, String estado) {
        Flux<ComprobantePagoDocument> base;

        if (contratoId != null) {
            base = comprobantePagoPort.findByContratoId(contratoId);
        } else {
            base = comprobantePagoPort.findByStoreId(storeId);
        }

        return base
                .filter(c -> tipo == null || tipo.equalsIgnoreCase(c.getTipo()))
                .filter(c -> estado == null || estado.equalsIgnoreCase(c.getEstado()));
    }

    // -------------------------------------------------------------------------
    // Buscar por ID
    // -------------------------------------------------------------------------

    public Mono<ComprobantePagoDocument> findById(String id) {
        return comprobantePagoPort.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Comprobante no encontrado: " + id)));
    }

    // -------------------------------------------------------------------------
    // Anular comprobante
    // -------------------------------------------------------------------------

    public Mono<ComprobantePagoDocument> anular(String id, String motivo,
                                                 String agenteId, String agenteNombre) {
        return comprobantePagoPort.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Comprobante no encontrado: " + id)))
                .flatMap(comprobante -> {
                    if (!"EMITIDO".equals(comprobante.getEstado())) {
                        return Mono.error(new ConflictException(
                                "Solo se puede anular un comprobante EMITIDO. Estado actual: "
                                        + comprobante.getEstado()));
                    }

                    comprobante.setEstado("ANULADO");
                    comprobante.setMotivoAnulacion(motivo);
                    comprobante.setAnuladoEn(new Date());

                    return comprobantePagoPort.save(comprobante)
                            .flatMap(saved -> {
                                if (saved.getContratoId() == null) {
                                    return Mono.just(saved);
                                }
                                EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                                        .contratoId(saved.getContratoId())
                                        .tipo("COMPROBANTE_ANULADO")
                                        .payload(Map.of(
                                                "comprobanteId", saved.getId(),
                                                "numeroCompleto", saved.getNumeroCompleto() != null
                                                        ? saved.getNumeroCompleto() : "",
                                                "motivo", motivo != null ? motivo : ""
                                        ))
                                        .usuarioId(agenteId)
                                        .usuarioNombre(agenteNombre)
                                        .automatico(false)
                                        .creadoEn(new Date())
                                        .build();

                                return eventoPort.append(saved.getContratoId(), evento)
                                        .thenReturn(saved);
                            });
                });
    }
}
