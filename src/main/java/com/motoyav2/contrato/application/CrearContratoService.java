package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.domain.port.in.CrearContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoNumberGenerator;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrearContratoService implements CrearContratoUseCase {

    private final ContratoRepository contratoRepository;
    private final ContratoNumberGenerator numberGenerator;

    @Override
    public Mono<Contrato> crear(
        DatosTitular titular,
        DatosFiador fiador,
        TiendaInfo tienda,
        DatosFinancieros datosFinancieros,
        String creadoPor,
        FacturaVehiculo factura,
        String evaluacionId
    ) {
        return numberGenerator.generate(evaluacionId)
                .flatMap(numeroContrato -> {
                    Instant now = Instant.now();
                    Contrato contrato = new Contrato(
                            UUID.randomUUID().toString(),
                            numeroContrato,
                            EstadoContrato.PENDIENTE_DOCUMENTOS,
                            FaseContrato.CREACION,
                            titular,
                            fiador,
                            tienda,
                            datosFinancieros,
                            BoucherPagoInicial.builder()
                                    .estadoValidacion(EstadoValidacion.PENDIENTE)
                                    .build(),
                            factura,
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            creadoPor,
                            evaluacionId,
                            null,
                            now,
                            now,
                            ContratoParaImprimir.builder().build()
                    );
                    return contratoRepository.save(contrato);
                });
    }
}
