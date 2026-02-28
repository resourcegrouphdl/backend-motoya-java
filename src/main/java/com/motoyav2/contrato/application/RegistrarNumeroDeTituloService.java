package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.port.in.RegistrarNumeroDeTituloUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.ForbiddenException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RegistrarNumeroDeTituloService implements RegistrarNumeroDeTituloUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> registrar(String contratoId, String tiendaId, String numeroDeTitulo) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.tienda() == null || !tiendaId.equals(contrato.tienda().tiendaId())) {
                        return Mono.error(new ForbiddenException("No tienes permiso para modificar este contrato"));
                    }
                    if (contrato.estado() != EstadoContrato.FIRMADO) {
                        return Mono.error(new ConflictException(
                                "El contrato debe estar en estado FIRMADO. Estado actual: " + contrato.estado()));
                    }

                    Instant now = Instant.now();
                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), contrato.estado(), contrato.fase(),
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            numeroDeTitulo, now,
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actaDeEntrega()
                    );

                    return contratoRepository.save(actualizado);
                });
    }
}
