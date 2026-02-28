package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.model.EvidenciaFirma;
import com.motoyav2.contrato.domain.port.in.SubirEvidenciaUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubirEvidenciaService implements SubirEvidenciaUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<EvidenciaFirma> subir(String contratoId, EvidenciaFirma evidencia) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    String evidenciaId = UUID.randomUUID().toString();

                    EvidenciaFirma nuevaEvidencia = EvidenciaFirma.builder()
                            .id(evidenciaId)
                            .tipoEvidencia(evidencia.tipoEvidencia())
                            .urlEvidencia(evidencia.urlEvidencia())
                            .nombreArchivo(evidencia.nombreArchivo())
                            .tipoArchivo(evidencia.tipoArchivo())
                            .tamanioBytes(evidencia.tamanioBytes())
                            .fechaSubida(Instant.now())
                            .descripcion(evidencia.descripcion())
                            .build();

                    List<EvidenciaFirma> evidencias = new ArrayList<>(
                            contrato.evidenciasFirma() != null ? contrato.evidenciasFirma() : List.of()
                    );
                    evidencias.add(nuevaEvidencia);

                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), contrato.estado(), contrato.fase(),
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), evidencias,
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), Instant.now(), contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actaDeEntrega()
                    );

                    return contratoRepository.save(actualizado)
                            .thenReturn(nuevaEvidencia);
                });
    }
}
