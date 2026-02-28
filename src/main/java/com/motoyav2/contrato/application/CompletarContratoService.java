package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.EvidenciaDocumento;
import com.motoyav2.contrato.domain.port.in.CompletarContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CompletarContratoService implements CompletarContratoUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> completar(String contratoId, String completadoPor) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.estado() != EstadoContrato.FIRMADO) {
                        return Mono.error(new BadRequestException(
                                "El contrato debe estar en estado FIRMADO. Estado actual: " + contrato.estado()));
                    }

                    if (contrato.numeroDeTitulo() == null || contrato.numeroDeTitulo().isBlank()) {
                        return Mono.error(new BadRequestException(
                                "Debe registrarse el número de título antes de completar el contrato"));
                    }

                    if (!esAprobado(contrato.tive())) {
                        return Mono.error(new BadRequestException("El documento TIVE debe estar APROBADO"));
                    }
                    if (!esAprobado(contrato.evidenciaSOAT())) {
                        return Mono.error(new BadRequestException("El documento SOAT debe estar APROBADO"));
                    }
                    if (!esAprobado(contrato.evidenciaPlacaRodaje())) {
                        return Mono.error(new BadRequestException("El documento de Placa de Rodaje debe estar APROBADO"));
                    }

                    ContratoStateMachine.validateTransition(contrato.estado(), EstadoContrato.COMPLETADO);

                    Instant now = Instant.now();
                    Contrato completado = new Contrato(
                            contrato.id(), contrato.numeroContrato(),
                            EstadoContrato.COMPLETADO, FaseContrato.FINALIZADO,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actaDeEntrega()
                    );

                    return contratoRepository.save(completado);
                });
    }

    private boolean esAprobado(EvidenciaDocumento ev) {
        return ev != null && EstadoValidacion.APROBADO == ev.estadoValidacion();
    }
}
