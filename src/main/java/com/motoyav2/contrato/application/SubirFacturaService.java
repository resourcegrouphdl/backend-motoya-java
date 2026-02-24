package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.model.FacturaVehiculo;
import com.motoyav2.contrato.domain.port.in.SubirFacturaUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubirFacturaService implements SubirFacturaUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<FacturaVehiculo> subir(String contratoId, FacturaVehiculo factura) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    String facturaId = UUID.randomUUID().toString();

                    FacturaVehiculo nuevaFactura = FacturaVehiculo.builder()
                            .id(facturaId)
                            .numeroFactura(factura.numeroFactura())
                            .urlDocumento(factura.urlDocumento())
                            .nombreArchivo(factura.nombreArchivo())
                            .tipoArchivo(factura.tipoArchivo())
                            .tamanioBytes(factura.tamanioBytes())
                            .fechaEmision(Instant.now())
                            .fechaSubida(Instant.now())
                            .marcaVehiculo(factura.marcaVehiculo())
                            .modeloVehiculo(factura.modeloVehiculo())
                            .anioVehiculo(factura.anioVehiculo())
                            .colorVehiculo(factura.colorVehiculo())
                            .serieMotor(factura.serieMotor())
                            .serieChasis(factura.serieChasis())
                            .estadoValidacion(EstadoValidacion.PENDIENTE)
                            .build();

                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), contrato.estado(), contrato.fase(),
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucherPagoInicial(), nuevaFactura,
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), Instant.now(), contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje()
                    );

                    return contratoRepository.save(actualizado)
                            .map(Contrato::facturaVehiculo);
                });
    }
}
