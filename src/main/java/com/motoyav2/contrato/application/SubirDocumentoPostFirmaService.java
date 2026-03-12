package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.EvidenciaDocumento;
import com.motoyav2.contrato.domain.port.in.SubirDocumentoPostFirmaUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.ConflictException;
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
public class SubirDocumentoPostFirmaService implements SubirDocumentoPostFirmaUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> subir(String contratoId, String tipo, List<EvidenciaDocumento> evidencias) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.estado() != EstadoContrato.FIRMADO) {
                        return Mono.error(new ConflictException(
                                "El contrato debe estar en estado FIRMADO. Estado actual: " + contrato.estado()));
                    }

                    boolean esActa = "ACTA_ENTREGA".equalsIgnoreCase(tipo);
                    EstadoValidacion estadoInicial = esActa ? EstadoValidacion.APROBADO : EstadoValidacion.PENDIENTE;

                    List<EvidenciaDocumento> nuevas = evidencias.stream()
                            .map(ev -> EvidenciaDocumento.builder()
                                    .id(UUID.randomUUID().toString())
                                    .tipoEvidencia(ev.tipoEvidencia())
                                    .urlEvidencia(ev.urlEvidencia())
                                    .nombreArchivo(ev.nombreArchivo())
                                    .tipoArchivo(ev.tipoArchivo())
                                    .tamanioBytes(ev.tamanioBytes())
                                    .fechaSubida(Instant.now())
                                    .descripcion(ev.descripcion())
                                    .estadoValidacion(estadoInicial)
                                    .build())
                            .toList();

                    Contrato actualizado = buildContratoConDocumento(contrato, tipo, nuevas);
                    return contratoRepository.save(actualizado);
                });
    }

    private Contrato buildContratoConDocumento(Contrato contrato, String tipo, List<EvidenciaDocumento> nuevas) {
        EvidenciaDocumento tive = contrato.tive();
        EvidenciaDocumento soat = contrato.evidenciaSOAT();
        EvidenciaDocumento placa = contrato.evidenciaPlacaRodaje();
        List<EvidenciaDocumento> actas = contrato.actasDeEntrega();

        switch (tipo.toUpperCase()) {
            case "TIVE" -> tive = nuevas.get(0);
            case "SOAT" -> soat = nuevas.get(0);
            case "PLACA_RODAJE" -> placa = nuevas.get(0);
            case "ACTA_ENTREGA" -> {
                List<EvidenciaDocumento> merged = new ArrayList<>(actas != null ? actas : List.of());
                merged.addAll(nuevas);
                actas = merged;
            }
            default -> throw new BadRequestException("Tipo de documento inválido: " + tipo + ". Use TIVE, SOAT, PLACA_RODAJE o ACTA_ENTREGA");
        }

        return new Contrato(
                contrato.id(), contrato.numeroContrato(), contrato.estado(), contrato.fase(),
                contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
                contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                contrato.motivoRechazo(), contrato.fechaCreacion(), Instant.now(), contrato.contratoParaImprimir(),
                contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                tive, soat, placa, actas
        );
    }
}
