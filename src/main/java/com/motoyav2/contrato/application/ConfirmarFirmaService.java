package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.port.in.ConfirmarFirmaUseCase;
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
public class ConfirmarFirmaService implements ConfirmarFirmaUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> confirmar(String contratoId, String confirmadoPor) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.estado() != EstadoContrato.FIRMA_PENDIENTE) {
                        return Mono.error(new BadRequestException(
                                "El contrato debe estar en FIRMA_PENDIENTE. Estado actual: " + contrato.estado()));
                    }

                    boolean todasAprobadas = contrato.evidenciasFirma() != null
                            && !contrato.evidenciasFirma().isEmpty()
                            && contrato.evidenciasFirma().stream()
                                .allMatch(e -> EstadoValidacion.APROBADO == e.estadoValidacion());

                    if (!todasAprobadas) {
                        return Mono.error(new BadRequestException(
                                "Todas las evidencias de firma deben estar APROBADAS antes de confirmar la firma"));
                    }

                    ContratoStateMachine.validateTransition(contrato.estado(), EstadoContrato.FIRMADO);

                    Instant now = Instant.now();
                    Contrato firmado = new Contrato(
                            contrato.id(), contrato.numeroContrato(),
                            EstadoContrato.FIRMADO, FaseContrato.VIGENTE,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucherPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje()
                    );

                    return contratoRepository.save(firmado);
                });
    }
}
