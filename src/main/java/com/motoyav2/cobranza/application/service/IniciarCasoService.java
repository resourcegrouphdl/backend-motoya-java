package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IniciarCasoService implements IniciarCasoUseCase {

    private final CasoCobranzaPort casoPort;
    private final EventoCobranzaPort eventoPort;

    @Override
    public Mono<CasoCobranzaDocument> ejecutar(IniciarCasoCommand command) {
        return casoPort.findById(command.contratoId())
                .defaultIfEmpty(new CasoCobranzaDocument())
                .flatMap(existente -> {
                    boolean esNuevo = existente.getContratoId() == null;

                    Date fechaVencimiento = parseFecha(command.fechaVencimientoPrimerCuotaImpaga());

                    CasoCobranzaDocument doc = CasoCobranzaDocument.builder()
                            .contratoId(command.contratoId())
                            .storeId(command.storeId())
                            .titular(command.titular())
                            // Campos planos para queries (denormalizados)
                            .clienteNombre(command.titular() != null
                                    ? command.titular().nombreCompleto() : null)
                            .clienteTelefono(command.titular() != null
                                    ? command.titular().getTelefono() : null)
                            .clienteDni(command.titular() != null
                                    ? command.titular().getNumeroDocumento() : null)
                            .motoDescripcion(command.motoDescripcion())
                            .capitalOriginal(command.capitalOriginal())
                            .saldoActual(command.saldoActual())
                            .nivelEstrategia(command.nivelEstrategia() != null
                                    ? command.nivelEstrategia() : "MORA_TEMPRANA")
                            .estadoCaso(command.estadoCaso() != null
                                    ? command.estadoCaso() : "EN_SEGUIMIENTO")
                            .agenteAsignadoId(command.agenteAsignadoId())
                            .agenteAsignadoNombre(command.agenteAsignadoNombre())
                            .fechaVencimientoPrimerCuotaImpaga(fechaVencimiento)
                            .cronograma(command.cronograma())
                            .creadoEn(esNuevo ? new Date() : existente.getCreadoEn())
                            .actualizadoEn(new Date())
                            .creadoPor(esNuevo ? command.creadoPor() : existente.getCreadoPor())
                            .build();

                    String tipoEvento = esNuevo ? "CASO_INICIADO" : "CASO_ACTUALIZADO";

                    return casoPort.save(doc)
                            .flatMap(saved -> eventoPort.append(saved.getContratoId(),
                                    EventoCobranzaDocument.builder()
                                            .contratoId(saved.getContratoId())
                                            .tipo(tipoEvento)
                                            .payload(Map.of(
                                                    "clienteNombre", saved.getClienteNombre() != null ? saved.getClienteNombre() : "",
                                                    "saldoActual", saved.getSaldoActual() != null ? saved.getSaldoActual() : 0.0,
                                                    "nivelEstrategia", saved.getNivelEstrategia() != null ? saved.getNivelEstrategia() : ""
                                            ))
                                            .usuarioId(command.creadoPor())
                                            .usuarioNombre(command.creadoPor())
                                            .automatico(false)
                                            .creadoEn(new Date())
                                            .build()
                            ).thenReturn(saved));
                });
    }

    private Date parseFecha(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(iso);
        } catch (Exception e) {
            log.warn("No se pudo parsear fecha: {}", iso);
            return null;
        }
    }
}
