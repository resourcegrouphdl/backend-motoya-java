package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.BoucherPagoInicial;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.FacturaVehiculo;
import com.motoyav2.contrato.domain.port.in.ValidarDocumentoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidarDocumentoService implements ValidarDocumentoUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> validar(String contratoId, String tipoDocumento, EstadoValidacion estado, String observacion, String validadoPor, String boucherId) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    List<BoucherPagoInicial> bouchers = contrato.boucheresPagoInicial() != null
                            ? contrato.boucheresPagoInicial() : List.of();
                    FacturaVehiculo factura = contrato.facturaVehiculo();
                    Instant now = Instant.now();

                    switch (tipoDocumento.toUpperCase()) {
                        case "BOUCHER" -> {
                            if (boucherId == null || boucherId.isBlank()) {
                                return Mono.error(new BadRequestException("boucherId es requerido para validar BOUCHER"));
                            }
                            BoucherPagoInicial actual = bouchers.stream()
                                    .filter(b -> boucherId.equals(b.id()))
                                    .findFirst()
                                    .orElse(null);
                            if (actual == null) {
                                return Mono.error(new NotFoundException("Boucher no encontrado: " + boucherId));
                            }
                            BoucherPagoInicial actualizado = BoucherPagoInicial.builder()
                                    .id(actual.id())
                                    .urlDocumento(actual.urlDocumento())
                                    .nombreArchivo(actual.nombreArchivo())
                                    .tipoArchivo(actual.tipoArchivo())
                                    .tamanioBytes(actual.tamanioBytes())
                                    .fechaSubida(actual.fechaSubida())
                                    .estadoValidacion(estado)
                                    .observacionesValidacion(observacion)
                                    .validadoPor(validadoPor)
                                    .fechaValidacion(now)
                                    .build();
                            bouchers = bouchers.stream()
                                    .map(b -> boucherId.equals(b.id()) ? actualizado : b)
                                    .toList();
                        }
                        case "FACTURA" -> {
                            FacturaVehiculo actual = contrato.facturaVehiculo();
                            factura = FacturaVehiculo.builder()
                                    .id(actual != null ? actual.id() : null)
                                    .numeroFactura(actual != null ? actual.numeroFactura() : null)
                                    .urlDocumento(actual != null ? actual.urlDocumento() : null)
                                    .nombreArchivo(actual != null ? actual.nombreArchivo() : null)
                                    .tipoArchivo(actual != null ? actual.tipoArchivo() : null)
                                    .tamanioBytes(actual != null ? actual.tamanioBytes() : null)
                                    .fechaEmision(actual != null ? actual.fechaEmision() : null)
                                    .fechaSubida(actual != null ? actual.fechaSubida() : null)
                                    .marcaVehiculo(actual != null ? actual.marcaVehiculo() : null)
                                    .modeloVehiculo(actual != null ? actual.modeloVehiculo() : null)
                                    .anioVehiculo(actual != null ? actual.anioVehiculo() : null)
                                    .colorVehiculo(actual != null ? actual.colorVehiculo() : null)
                                    .serieMotor(actual != null ? actual.serieMotor() : null)
                                    .serieChasis(actual != null ? actual.serieChasis() : null)
                                    .estadoValidacion(estado)
                                    .observacionesValidacion(observacion)
                                    .validadoPor(validadoPor)
                                    .fechaValidacion(now)
                                    .build();
                        }
                        default -> {
                            return Mono.error(new BadRequestException("Tipo de documento inválido: " + tipoDocumento));
                        }
                    }

                    EstadoContrato nuevoEstado = contrato.estado();
                    FaseContrato nuevaFase = contrato.fase();

                    boolean algoBoucherAprobado = bouchers.stream()
                            .anyMatch(b -> b.estadoValidacion() == EstadoValidacion.APROBADO);

                    if (algoBoucherAprobado
                            && factura != null && factura.estadoValidacion() == EstadoValidacion.APROBADO) {
                        nuevoEstado = EstadoContrato.EN_VALIDACION;
                        nuevaFase = FaseContrato.VALIDACION_DOCUMENTOS;
                    }

                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), nuevoEstado, nuevaFase,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            bouchers, factura,
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actaDeEntrega()
                    );

                    return contratoRepository.save(actualizado);
                });
    }
}