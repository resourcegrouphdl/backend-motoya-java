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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubirDocumentoPostFirmaService implements SubirDocumentoPostFirmaUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> subir(String contratoId, String tipo, EvidenciaDocumento evidencia) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.estado() != EstadoContrato.FIRMADO) {
                        return Mono.error(new ConflictException(
                                "El contrato debe estar en estado FIRMADO. Estado actual: " + contrato.estado()));
                    }

                    EstadoValidacion estadoInicial = "ACTA_ENTREGA".equalsIgnoreCase(tipo)
                            ? EstadoValidacion.APROBADO
                            : EstadoValidacion.PENDIENTE;

                    EvidenciaDocumento nuevaEvidencia = EvidenciaDocumento.builder()
                            .id(UUID.randomUUID().toString())
                            .tipoEvidencia(evidencia.tipoEvidencia())
                            .urlEvidencia(evidencia.urlEvidencia())
                            .nombreArchivo(evidencia.nombreArchivo())
                            .tipoArchivo(evidencia.tipoArchivo())
                            .tamanioBytes(evidencia.tamanioBytes())
                            .fechaSubida(Instant.now())
                            .descripcion(evidencia.descripcion())
                            .estadoValidacion(estadoInicial)
                            .build();

                    Contrato actualizado = buildContratoConDocumento(contrato, tipo, nuevaEvidencia);
                    return contratoRepository.save(actualizado);
                });
    }

    private Contrato buildContratoConDocumento(Contrato contrato, String tipo, EvidenciaDocumento nueva) {
        EvidenciaDocumento tive = contrato.tive();
        EvidenciaDocumento soat = contrato.evidenciaSOAT();
        EvidenciaDocumento placa = contrato.evidenciaPlacaRodaje();
        EvidenciaDocumento acta = contrato.actaDeEntrega();

        switch (tipo.toUpperCase()) {
            case "TIVE" -> tive = nueva;
            case "SOAT" -> soat = nueva;
            case "PLACA_RODAJE" -> placa = nueva;
            case "ACTA_ENTREGA" -> acta = nueva;
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
                tive, soat, placa, acta
        );
    }
}
