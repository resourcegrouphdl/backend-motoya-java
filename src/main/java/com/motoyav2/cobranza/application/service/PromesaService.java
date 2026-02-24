package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.CerrarPromesaUseCase;
import com.motoyav2.cobranza.application.port.in.RegistrarPromesaUseCase;
import com.motoyav2.cobranza.application.port.in.command.CerrarPromesaCommand;
import com.motoyav2.cobranza.application.port.in.command.RegistrarPromesaCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromesaService implements RegistrarPromesaUseCase, CerrarPromesaUseCase {

    private final CasoCobranzaPort casoPort;
    private final PromesaPort promesaPort;
    private final EventoCobranzaPort eventoPort;

    // -------------------------------------------------------------------------
    // RegistrarPromesaUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<String> ejecutar(RegistrarPromesaCommand command) {
        return casoPort.findById(command.contratoId())
                .switchIfEmpty(Mono.error(new NotFoundException("Caso no encontrado: " + command.contratoId())))
                // Regla: solo una promesa VIGENTE por caso
                .flatMap(caso -> promesaPort.findVigente(command.contratoId())
                        .flatMap(__ -> Mono.<PromesaDocument>error(
                                new ConflictException("Ya existe una promesa vigente para: " + command.contratoId())))
                        .switchIfEmpty(Mono.just(PromesaDocument.builder()
                                .contratoId(command.contratoId())
                                .fecha(command.fecha())
                                .monto(command.monto())
                                .estado("VIGENTE")
                                .observaciones(command.observaciones())
                                .fechaRegistro(new Date())
                                .registradaPor(command.agenteId())
                                .registradaPorNombre(command.agenteNombre())
                                .build()))
                        .flatMap(promesa -> promesaPort.save(command.contratoId(), promesa))
                        .flatMap(promesa -> {
                            caso.setEstadoCaso("PROMESA_VIGENTE");
                            caso.setUltimaGestion(new Date());
                            caso.setUltimaGestionResumen("Promesa registrada: S/ " + command.monto());
                            caso.setActualizadoEn(new Date());

                            EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                                    .contratoId(command.contratoId())
                                    .tipo("PROMESA_REGISTRADA")
                                    .payload(Map.of(
                                            "promesaId", promesa.getId(),
                                            "fechaPromesa", command.fecha(),
                                            "montoPrometido", command.monto(),
                                            "observaciones", command.observaciones() != null ? command.observaciones() : ""
                                    ))
                                    .usuarioId(command.agenteId())
                                    .usuarioNombre(command.agenteNombre())
                                    .automatico(false)
                                    .creadoEn(new Date())
                                    .build();

                            return casoPort.save(caso)
                                    .then(eventoPort.append(command.contratoId(), evento))
                                    .thenReturn(promesa.getId());
                        })
                );
    }

    // -------------------------------------------------------------------------
    // CerrarPromesaUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> ejecutar(CerrarPromesaCommand command) {
        return promesaPort.findById(command.contratoId(), command.promesaId())
                .switchIfEmpty(Mono.error(new NotFoundException("Promesa no encontrada: " + command.promesaId())))
                .flatMap(promesa -> {
                    if (!"VIGENTE".equals(promesa.getEstado())) {
                        return Mono.error(new BadRequestException(
                                "Solo se puede cerrar una promesa VIGENTE. Estado actual: " + promesa.getEstado()));
                    }
                    promesa.setEstado(command.resultado());
                    promesa.setCerradaEn(new Date());

                    if ("CUMPLIDA".equals(command.resultado())) {
                        promesa.setMontoPagado(command.montoPagado());
                    } else {
                        promesa.setMotivoCierre(command.motivo());
                    }

                    return promesaPort.save(command.contratoId(), promesa)
                            .flatMap(savedPromesa -> casoPort.findById(command.contratoId())
                                    .flatMap(caso -> {
                                        String nuevoEstado = resolverEstadoCaso(command.resultado());
                                        caso.setEstadoCaso(nuevoEstado);
                                        caso.setUltimaGestion(new Date());
                                        caso.setUltimaGestionResumen("Promesa " + command.resultado().toLowerCase());
                                        caso.setActualizadoEn(new Date());

                                        String tipoEvento = "PROMESA_" + command.resultado();
                                        Map<String, Object> payload = new HashMap<>();
                                        payload.put("promesaId", command.promesaId());
                                        payload.put("resultado", command.resultado());
                                        if (command.montoPagado() != null) payload.put("montoPagado", command.montoPagado());
                                        if (command.motivo() != null) payload.put("motivo", command.motivo());

                                        EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                                                .contratoId(command.contratoId())
                                                .tipo(tipoEvento)
                                                .payload(payload)
                                                .usuarioId(command.usuarioId())
                                                .usuarioNombre(command.usuarioNombre())
                                                .automatico(false)
                                                .creadoEn(new Date())
                                                .build();

                                        return casoPort.save(caso)
                                                .then(eventoPort.append(command.contratoId(), evento))
                                                .then();
                                    })
                            );
                });
    }

    private String resolverEstadoCaso(String resultadoPromesa) {
        return switch (resultadoPromesa) {
            case "CUMPLIDA"    -> "EN_SEGUIMIENTO";
            case "INCUMPLIDA"  -> "PROMESA_INCUMPLIDA";
            default            -> "EN_SEGUIMIENTO";
        };
    }
}
