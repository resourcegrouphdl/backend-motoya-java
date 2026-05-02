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
                .cliente(command.clienteNombre())
                .estado("PENDIENTE")
                .fuente(command.fuente())
                .imagenPath(command.imagenPath())
                .thumbPath(command.thumbPath())
                .montoDetectado(command.montoDetectado())
                .montoEsperado(command.montoEsperado())
                .ocrResultado(command.ocrResultado())
                .creadoEn(new Date())
                .creadoPor(command.subioPor())
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
                    // IDEMPOTENCIA: ya fue aprobado — retornar comprobanteId existente (puede ser "" si se aprobó sin comprobante)
                    if ("APROBADO".equals(voucher.getEstado())) {
                        log.info("[AprobarVoucher] Idempotente — voucher {} ya aprobado", command.voucherId());
                        return Mono.just(voucher.getComprobanteId() != null ? voucher.getComprobanteId() : "");
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
                .flatMap(caso -> command.serie() != null
                        ? procesarConComprobante(voucher, command, caso)
                        : procesarSinComprobante(voucher, command, caso));
    }

    private Mono<String> procesarConComprobante(VoucherDocument voucher, AprobarVoucherCommand command,
                                                 CasoCobranzaDocument caso) {
        return numeradorPort.siguienteNumero(command.serie())
                .flatMap(numeroCompleto -> {
                    double monto    = voucher.getMontoDetectado();
                    double subTotal = monto / (1 + IGV);
                    double igv      = monto - subTotal;
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
                .flatMap(comprobante ->
                        aplicarPago(voucher, command, caso, comprobante.getId(),
                                "Pago vía voucher - " + comprobante.getNumeroCompleto())
                                .thenReturn(comprobante.getId()));
    }

    private Mono<String> procesarSinComprobante(VoucherDocument voucher, AprobarVoucherCommand command,
                                                 CasoCobranzaDocument caso) {
        log.info("[AprobarVoucher] Sin comprobante — facturación pendiente de implementación | voucherId={}",
                voucher.getId());
        return aplicarPago(voucher, command, caso, null, "Pago vía voucher")
                .thenReturn("");
    }

    private Mono<Void> aplicarPago(VoucherDocument voucher, AprobarVoucherCommand command,
                                    CasoCobranzaDocument caso, String comprobanteId, String descripcion) {
        double saldoAnterior = caso.getSaldoActual() != null ? caso.getSaldoActual() : 0.0;
        double saldoNuevo    = saldoAnterior - voucher.getMontoDetectado();

        MovimientoDocument movimiento = MovimientoDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(voucher.getContratoId())
                .tipo("PAGO_CUOTA")
                .monto(-voucher.getMontoDetectado())
                .saldoAnterior(saldoAnterior)
                .saldoNuevo(saldoNuevo)
                .descripcion(descripcion)
                .voucherId(voucher.getId())
                .comprobanteId(comprobanteId)
                .autorizadoPor(command.agenteId())
                .creadoEn(new Date())
                .build();

        voucher.setEstado("APROBADO");
        voucher.setAprobadoPor(command.agenteId());
        voucher.setAprobadoPorNombre(command.agenteNombre());
        voucher.setProcesadoEn(new Date());
        voucher.setActualizadoEn(new Date());
        voucher.setActualizadoPor(command.agenteId());
        voucher.setComprobanteId(comprobanteId);

        String fechaPago       = resolveFechaPago(voucher, command);
        int    cuotasMarcadas  = CuotaAplicador.aplicar(
                caso.getCronograma(), voucher.getMontoDetectado(), fechaPago, null);
        log.info("[AprobarVoucher] Cuotas marcadas PAGADA | contratoId={} count={} fechaPago={}",
                voucher.getContratoId(), cuotasMarcadas, fechaPago);

        caso.setSaldoActual(saldoNuevo);
        caso.setTotalPagado((caso.getTotalPagado() != null ? caso.getTotalPagado() : 0.0)
                + voucher.getMontoDetectado());
        caso.setUltimaGestion(new Date());
        caso.setUltimaGestionResumen("Pago aplicado: S/ " + voucher.getMontoDetectado());
        caso.setActualizadoEn(new Date());
        caso.setActualizadoPor(command.agenteId());

        // Payload del evento — comprobanteId es opcional (puede ser null si aún no hay facturación)
        java.util.HashMap<String, Object> payloadAprobado = new java.util.HashMap<>();
        payloadAprobado.put("voucherId",     voucher.getId());
        payloadAprobado.put("montoAplicado", voucher.getMontoDetectado());
        payloadAprobado.put("saldoAnterior", saldoAnterior);
        payloadAprobado.put("saldoNuevo",    saldoNuevo);
        if (comprobanteId != null && !comprobanteId.isEmpty()) {
            payloadAprobado.put("comprobanteId", comprobanteId);
        }

        EventoCobranzaDocument eventoAprobado = EventoCobranzaDocument.builder()
                .contratoId(voucher.getContratoId())
                .tipo("VOUCHER_APROBADO")
                .payload(payloadAprobado)
                .usuarioId(command.agenteId())
                .usuarioNombre(command.agenteNombre())
                .automatico(false)
                .creadoEn(new Date())
                .build();

        EventoCobranzaDocument eventoPago = EventoCobranzaDocument.builder()
                .contratoId(voucher.getContratoId())
                .tipo("PAGO_APLICADO")
                .payload(Map.of(
                        "voucherId",     voucher.getId(),
                        "montoAplicado", voucher.getMontoDetectado(),
                        "saldoAnterior", saldoAnterior,
                        "saldoNuevo",    saldoNuevo
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
                .then();
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
                    voucher.setActualizadoEn(new Date());
                    voucher.setActualizadoPor(command.agenteId());

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
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Determina la fecha de pago a registrar en el cronograma:
     *   1. fechaPagoReal del comando (retroactivo / migración)
     *   2. Fecha extraída por OCR del voucher
     *   3. Hoy como fallback
     */
    private String resolveFechaPago(VoucherDocument voucher, AprobarVoucherCommand command) {
        if (command.fechaPagoReal() != null && !command.fechaPagoReal().isBlank()) {
            return command.fechaPagoReal();
        }
        if (voucher.getOcrResultado() != null && voucher.getOcrResultado().getFecha() != null
                && !voucher.getOcrResultado().getFecha().isBlank()) {
            return voucher.getOcrResultado().getFecha();
        }
        return LocalDate.now().toString();
    }

    // -------------------------------------------------------------------------
    // ListarVouchersUseCase
    // -------------------------------------------------------------------------

    @Override
    public Flux<VoucherDocument> ejecutar(String storeId, String estado) {
        return voucherPort.findByStoreIdAndEstado(storeId, estado);
    }
}
