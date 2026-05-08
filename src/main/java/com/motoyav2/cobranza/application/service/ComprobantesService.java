package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.ComprobantePagoPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.ComprobantePagoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import java.util.UUID;
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
                                                 String tipo, String estado,
                                                 String fechaDesde, String fechaHasta) {
        Flux<ComprobantePagoDocument> base;

        if (contratoId != null) {
            base = comprobantePagoPort.findByContratoId(contratoId);
        } else {
            base = comprobantePagoPort.findByStoreId(storeId);
        }

        return base
                .filter(c -> tipo == null || tipo.equalsIgnoreCase(c.getTipo()))
                .filter(c -> estado == null || estado.equalsIgnoreCase(c.getEstado()))
                .filter(c -> fechaDesde == null || (c.getFechaEmision() != null && c.getFechaEmision().compareTo(fechaDesde) >= 0))
                .filter(c -> fechaHasta == null || (c.getFechaEmision() != null && c.getFechaEmision().compareTo(fechaHasta) <= 0));
    }

    // -------------------------------------------------------------------------
    // Buscar por ID
    // -------------------------------------------------------------------------

    public Mono<ComprobantePagoDocument> findById(String id) {
        return comprobantePagoPort.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Comprobante no encontrado: " + id)));
    }

    // -------------------------------------------------------------------------
    // Registrar boleta manual (PDF subido por administrador)
    // -------------------------------------------------------------------------

    public Mono<ComprobantePagoDocument> registrarBoletaManual(
            String contratoId, String pdfPath, Double monto, String fechaEmision) {
        String shortId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ComprobantePagoDocument doc = ComprobantePagoDocument.builder()
                .tipo("BOLETA")
                .estado("EMITIDO")
                .fuente("BOLETA_MANUAL")
                .contratoId(contratoId)
                .numeroCompleto("BM-" + shortId)
                .pdfPath(pdfPath)
                .total(monto != null ? monto : 0.0)
                .fechaEmision(fechaEmision)
                .creadoEn(new Date())
                .build();
        return comprobantePagoPort.save(doc);
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
