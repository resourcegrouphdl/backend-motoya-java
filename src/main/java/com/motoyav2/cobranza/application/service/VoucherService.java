package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.*;
import com.motoyav2.cobranza.application.port.in.command.AprobarVoucherCommand;
import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import com.motoyav2.cobranza.application.port.in.command.RechazarVoucherCommand;
import com.motoyav2.cobranza.application.port.out.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.EmisorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.ItemComprobanteDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.ReceptorComprobanteDocument;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService implements RecibirVoucherUseCase, AprobarVoucherUseCase,
        RechazarVoucherUseCase, ListarVouchersUseCase {

    private static final double IGV = 0.18;

    private final VoucherPort voucherPort;
    private final CasoCobranzaPort casoPort;
    private final MovimientoPort movimientoPort;
    private final ComprobantePagoPort comprobantePagoPort;
    private final AlertaCobranzaPort alertaPort;
    private final EventoCobranzaPort eventoPort;
    private final NumeradorPort numeradorPort;

    // -------------------------------------------------------------------------
    // RecibirVoucherUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<String> ejecutar(RecibirVoucherCommand command) {
        VoucherDocument voucher = VoucherDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(command.contratoId())
                .storeId(command.storeId())
                .estado("PENDIENTE")
                .imagenPath(command.imagenPath())
                .thumbPath(command.thumbPath())
                .montoDetectado(command.montoDetectado())
                .creadoEn(new Date())
                .build();

        AlertaCobranzaDocument alerta = AlertaCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .tipo("VOUCHER_PENDIENTE")
                .nivel("WARNING")
                .titulo("Voucher pendiente de revisión")
                .descripcion("Se recibió un voucher que requiere aprobación")
                .contratoId(command.contratoId())
                .storeId(command.storeId())
                .leida(false)
                .descartada(false)
                .creadoEn(new Date())
                .build();

        return voucherPort.save(voucher)
                .flatMap(saved -> {
                    Mono<Void> registrarEvento = command.contratoId() != null
                            ? eventoPort.append(command.contratoId(), EventoCobranzaDocument.builder()
                                    .contratoId(command.contratoId())
                                    .tipo("VOUCHER_RECIBIDO")
                                    .payload(Map.of("voucherId", saved.getId(),
                                            "montoDetectado", command.montoDetectado() != null ? command.montoDetectado() : 0.0))
                                    .usuarioId(command.subioPor())
                                    .usuarioNombre(command.subioPor())
                                    .automatico(false)
                                    .creadoEn(new Date())
                                    .build()).then()
                            : Mono.empty();

                    return alertaPort.save(alerta)
                            .then(registrarEvento)
                            .thenReturn(saved.getId());
                });
    }

    // -------------------------------------------------------------------------
    // AprobarVoucherUseCase — Saga: Voucher → Movimiento → Comprobante → Saldo → Eventos
    // -------------------------------------------------------------------------

    @Override
    public Mono<String> ejecutar(AprobarVoucherCommand command) {
        return voucherPort.findById(command.voucherId())
                .switchIfEmpty(Mono.error(new NotFoundException("Voucher no encontrado: " + command.voucherId())))
                .flatMap(voucher -> {
                    // IDEMPOTENCIA: ya fue aprobado — retornar comprobanteId existente
                    if ("APROBADO".equals(voucher.getEstado())) {
                        log.info("[AprobarVoucher] Idempotente — voucher {} ya aprobado", command.voucherId());
                        return Mono.just(voucher.getComprobanteId());
                    }
                    if (!"PENDIENTE".equals(voucher.getEstado())) {
                        return Mono.error(new ConflictException(
                                "Voucher no está en estado PENDIENTE. Estado actual: " + voucher.getEstado()));
                    }
                    if (voucher.getContratoId() == null) {
                        return Mono.error(new BadRequestException(
                                "El voucher no tiene un contratoId asignado. Vincúlelo antes de aprobar."));
                    }
                    if (voucher.getMontoDetectado() == null || voucher.getMontoDetectado() <= 0) {
                        return Mono.error(new BadRequestException("El voucher no tiene monto detectado válido."));
                    }
                    return procesarAprobacion(voucher, command);
                });
    }

    private Mono<String> procesarAprobacion(VoucherDocument voucher, AprobarVoucherCommand command) {
        return casoPort.findById(voucher.getContratoId())
                .switchIfEmpty(Mono.error(new NotFoundException("Caso no encontrado: " + voucher.getContratoId())))
                .flatMap(caso -> numeradorPort.siguienteNumero(command.serie())
                        .flatMap(numeroCompleto -> {
                            double monto = voucher.getMontoDetectado();
                            double subTotal = monto / (1 + IGV);
                            double igv = monto - subTotal;
                            String[] partes = numeroCompleto.split("-");

                            ComprobantePagoDocument comprobante = ComprobantePagoDocument.builder()
                                    .id(UUID.randomUUID().toString())
                                    .serie(partes[0])
                                    .numero(partes[1])
                                    .numeroCompleto(numeroCompleto)
                                    .tipo(command.serie().startsWith("B") ? "BOLETA" : "FACTURA")
                                    .estado("PENDIENTE")
                                    .contratoId(voucher.getContratoId())
                                    .voucherId(voucher.getId())
                                    .storeId(caso.getStoreId())
                                    .emisor(EmisorDocument.builder()
                                            .ruc(command.rucEmisor())
                                            .razonSocial(command.razonSocialEmisor())
                                            .direccion(command.direccionEmisor())
                                            .build())
                                    .receptor(ReceptorComprobanteDocument.builder()
                                            .tipoDocumento(command.tipoDocumentoReceptor())
                                            .numeroDocumento(command.numeroDocumentoReceptor())
                                            .nombreCompleto(command.nombreReceptor())
                                            .build())
                                    .items(List.of(ItemComprobanteDocument.builder()
                                            .descripcion(command.descripcionItem() != null
                                                    ? command.descripcionItem()
                                                    : "Pago - Contrato " + voucher.getContratoId())
                                            .cantidad(1)
                                            .precioUnitario(subTotal)
                                            .totalItem(subTotal)
                                            .build()))
                                    .subTotal(Math.round(subTotal * 100.0) / 100.0)
                                    .igv(Math.round(igv * 100.0) / 100.0)
                                    .total(monto)
                                    .fechaEmision(LocalDate.now().toString())
                                    .intentosSunat(0)
                                    .creadoEn(new Date())
                                    .build();

                            return comprobantePagoPort.save(comprobante);
                        })
                        .flatMap(comprobante -> {
                            double saldoAnterior = caso.getSaldoActual() != null ? caso.getSaldoActual() : 0.0;
                            double saldoNuevo = saldoAnterior - voucher.getMontoDetectado();

                            MovimientoDocument movimiento = MovimientoDocument.builder()
                                    .id(UUID.randomUUID().toString())
                                    .contratoId(voucher.getContratoId())
                                    .tipo("PAGO_CUOTA")
                                    .monto(-voucher.getMontoDetectado())
                                    .saldoAnterior(saldoAnterior)
                                    .saldoNuevo(saldoNuevo)
                                    .descripcion("Pago vía voucher - " + comprobante.getNumeroCompleto())
                                    .voucherId(voucher.getId())
                                    .comprobanteId(comprobante.getId())
                                    .autorizadoPor(command.agenteId())
                                    .creadoEn(new Date())
                                    .build();

                            // Actualizar voucher
                            voucher.setEstado("APROBADO");
                            voucher.setAprobadoPor(command.agenteId());
                            voucher.setAprobadoPorNombre(command.agenteNombre());
                            voucher.setProcesadoEn(new Date());
                            voucher.setComprobanteId(comprobante.getId());

                            // Actualizar saldo del caso
                            caso.setSaldoActual(saldoNuevo);
                            caso.setTotalPagado((caso.getTotalPagado() != null ? caso.getTotalPagado() : 0.0)
                                    + voucher.getMontoDetectado());
                            caso.setUltimaGestion(new Date());
                            caso.setUltimaGestionResumen("Pago aplicado: S/ " + voucher.getMontoDetectado());
                            caso.setActualizadoEn(new Date());

                            EventoCobranzaDocument eventoAprobado = EventoCobranzaDocument.builder()
                                    .contratoId(voucher.getContratoId())
                                    .tipo("VOUCHER_APROBADO")
                                    .payload(Map.of(
                                            "voucherId", voucher.getId(),
                                            "montoAplicado", voucher.getMontoDetectado(),
                                            "saldoAnterior", saldoAnterior,
                                            "saldoNuevo", saldoNuevo,
                                            "comprobanteId", comprobante.getId()
                                    ))
                                    .usuarioId(command.agenteId())
                                    .usuarioNombre(command.agenteNombre())
                                    .automatico(false)
                                    .creadoEn(new Date())
                                    .build();

                            EventoCobranzaDocument eventoPago = EventoCobranzaDocument.builder()
                                    .contratoId(voucher.getContratoId())
                                    .tipo("PAGO_APLICADO")
                                    .payload(Map.of(
                                            "voucherId", voucher.getId(),
                                            "montoAplicado", voucher.getMontoDetectado(),
                                            "saldoAnterior", saldoAnterior,
                                            "saldoNuevo", saldoNuevo
                                    ))
                                    .usuarioId(command.agenteId())
                                    .usuarioNombre(command.agenteNombre())
                                    .automatico(false)
                                    .creadoEn(new Date())
                                    .build();

                            return movimientoPort.append(voucher.getContratoId(), movimiento)
                                    .then(voucherPort.save(voucher))
                                    .then(casoPort.save(caso))
                                    .then(eventoPort.append(voucher.getContratoId(), eventoAprobado))
                                    .then(eventoPort.append(voucher.getContratoId(), eventoPago))
                                    .thenReturn(comprobante.getId());
                        })
                );
    }

    // -------------------------------------------------------------------------
    // RechazarVoucherUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> ejecutar(RechazarVoucherCommand command) {
        return voucherPort.findById(command.voucherId())
                .switchIfEmpty(Mono.error(new NotFoundException("Voucher no encontrado: " + command.voucherId())))
                .flatMap(voucher -> {
                    if (!"PENDIENTE".equals(voucher.getEstado())) {
                        return Mono.error(new ConflictException(
                                "Solo se puede rechazar un voucher PENDIENTE. Estado: " + voucher.getEstado()));
                    }
                    voucher.setEstado("RECHAZADO");
                    voucher.setRechazadoPor(command.agenteId());
                    voucher.setMotivoRechazo(command.motivoRechazo());
                    voucher.setObservacionesRechazo(command.observaciones());
                    voucher.setProcesadoEn(new Date());

                    Mono<Void> registrarEvento = voucher.getContratoId() != null
                            ? eventoPort.append(voucher.getContratoId(), EventoCobranzaDocument.builder()
                                    .contratoId(voucher.getContratoId())
                                    .tipo("VOUCHER_RECHAZADO")
                                    .payload(Map.of(
                                            "voucherId", voucher.getId(),
                                            "motivo", command.motivoRechazo(),
                                            "observaciones", command.observaciones() != null ? command.observaciones() : ""
                                    ))
                                    .usuarioId(command.agenteId())
                                    .usuarioNombre(command.agenteNombre())
                                    .automatico(false)
                                    .creadoEn(new Date())
                                    .build()).then()
                            : Mono.empty();

                    return voucherPort.save(voucher).then(registrarEvento);
                });
    }

    // -------------------------------------------------------------------------
    // ListarVouchersUseCase
    // -------------------------------------------------------------------------

    @Override
    public Flux<VoucherDocument> ejecutar(String storeId, String estado) {
        return voucherPort.findByStoreIdAndEstado(storeId, estado);
    }
}
