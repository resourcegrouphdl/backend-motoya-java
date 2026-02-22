package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.BoucherPagoInicial;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.port.in.SubirBoucherUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubirBoucherService implements SubirBoucherUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<BoucherPagoInicial> subir(String contratoId, BoucherPagoInicial boucher) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    String boucherId = boucher.id() != null ? boucher.id() : UUID.randomUUID().toString();

                    BoucherPagoInicial nuevoBoucher = BoucherPagoInicial.builder()
                            .id(boucherId)
                            .urlDocumento(boucher.urlDocumento())
                            .nombreArchivo(boucher.nombreArchivo())
                            .tipoArchivo(boucher.tipoArchivo())
                            .tamanioBytes(boucher.tamanioBytes())
                            .fechaSubida(Instant.now())
                            .estadoValidacion(EstadoValidacion.PENDIENTE)
                            .build();

                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), contrato.estado(), contrato.fase(),
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            nuevoBoucher, contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), Instant.now(), ContratoParaImprimir.builder().build()
                    );

                    return contratoRepository.save(actualizado)
                            .map(Contrato::boucherPagoInicial);
                });
    }
}
