package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.contrato.domain.port.in.RechazarContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RechazarContratoService implements RechazarContratoUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> rechazar(String contratoId, String motivo, String rechazadoPor) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.estado() != EstadoContrato.EN_VALIDACION) {
                        return Mono.error(new BadRequestException(
                                "El contrato debe estar EN_VALIDACION para ser rechazado. Estado actual: " + contrato.estado()));
                    }

                    ContratoStateMachine.validateTransition(contrato.estado(), EstadoContrato.RECHAZADO);

                    Contrato rechazado = new Contrato(
                            contrato.id(), contrato.numeroContrato(),
                            EstadoContrato.RECHAZADO, FaseContrato.FINALIZADO,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucherPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            motivo, contrato.fechaCreacion(), Instant.now(), contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje()
                    );

                    return contratoRepository.save(rechazado);
                });
    }
}
