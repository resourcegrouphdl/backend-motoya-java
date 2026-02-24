package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.EvidenciaDocumento;
import com.motoyav2.contrato.domain.port.in.ValidarEvidenciaDocumentoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ValidarEvidenciaDocumentoService implements ValidarEvidenciaDocumentoUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> validar(String contratoId, String tipo, EstadoValidacion estado,
                                  String observacion, String validadoPor) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    Instant now = Instant.now();

                    EvidenciaDocumento tive = contrato.tive();
                    EvidenciaDocumento soat = contrato.evidenciaSOAT();
                    EvidenciaDocumento placa = contrato.evidenciaPlacaRodaje();

                    switch (tipo.toUpperCase()) {
                        case "TIVE" -> {
                            if (tive == null) {
                                return Mono.error(new BadRequestException("El documento TIVE no ha sido subido"));
                            }
                            tive = buildValidado(tive, estado, observacion, validadoPor, now);
                        }
                        case "SOAT" -> {
                            if (soat == null) {
                                return Mono.error(new BadRequestException("El documento SOAT no ha sido subido"));
                            }
                            soat = buildValidado(soat, estado, observacion, validadoPor, now);
                        }
                        case "PLACA_RODAJE" -> {
                            if (placa == null) {
                                return Mono.error(new BadRequestException("El documento de Placa de Rodaje no ha sido subido"));
                            }
                            placa = buildValidado(placa, estado, observacion, validadoPor, now);
                        }
                        default -> {
                            return Mono.error(new BadRequestException(
                                    "Tipo inválido: " + tipo + ". Use TIVE, SOAT o PLACA_RODAJE"));
                        }
                    }

                    EstadoContrato nuevoEstado = contrato.estado();
                    FaseContrato nuevaFase = contrato.fase();

                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), nuevoEstado, nuevaFase,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucherPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            tive, soat, placa
                    );

                    return contratoRepository.save(actualizado);
                });
    }

    private EvidenciaDocumento buildValidado(EvidenciaDocumento ev, EstadoValidacion estado,
                                             String observacion, String validadoPor, Instant now) {
        return EvidenciaDocumento.builder()
                .id(ev.id())
                .tipoEvidencia(ev.tipoEvidencia())
                .urlEvidencia(ev.urlEvidencia())
                .nombreArchivo(ev.nombreArchivo())
                .tipoArchivo(ev.tipoArchivo())
                .tamanioBytes(ev.tamanioBytes())
                .fechaSubida(ev.fechaSubida())
                .descripcion(ev.descripcion())
                .estadoValidacion(estado)
                .validadoPor(validadoPor)
                .fechaValidacion(now)
                .observacionesValidacion(observacion)
                .build();
    }

}
