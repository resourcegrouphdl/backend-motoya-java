package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.RecibirVoucherUseCase;
import com.motoyav2.cobranza.application.port.in.RegistrarPagoManualUseCase;
import com.motoyav2.cobranza.application.port.in.command.RegistrarPagoManualCommand;
import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MovimientoPort;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.OcrResultadoDocument;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registra un pago de forma manual desde el panel de administración.
 *
 * Hay dos rutas según si se adjunta un comprobante (imagen):
 *
 * A) SIN imagen — pago directo (efectivo confirmado, migración):
 *    Marca cuotas PAGADA + actualiza saldo + Movimiento + Evento. Listo.
 *
 * B) CON imagen — se integra al flujo normal de vouchers:
 *    Crea VoucherDocument en estado PENDIENTE con los datos ingresados por
 *    el agente como hints OCR (monto, fecha). El revisor lo aprueba desde la
 *    vista de Vouchers → AprobarVoucherUseCase marca cuotas + crea Comprobante.
 *    El saldo NO se toca aquí; lo toca AprobarVoucherUseCase al aprobar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrarPagoManualService implements RegistrarPagoManualUseCase {

    private final CasoCobranzaPort    casoPort;
    private final MovimientoPort      movimientoPort;
    private final VoucherPort         voucherPort;
    private final EventoCobranzaPort  eventoPort;
    private final RecibirVoucherUseCase recibirVoucherUseCase;

    @Override
    public Mono<Result> ejecutar(RegistrarPagoManualCommand command) {
        boolean tieneImagen = command.imagenPath() != null && !command.imagenPath().isBlank();

        return tieneImagen
                ? registrarConVoucher(command)
                : registrarDirecto(command);
    }

    // ─── Ruta A: sin imagen — pago directo ───────────────────────────────────

    private Mono<Result> registrarDirecto(RegistrarPagoManualCommand command) {
        return casoPort.findById(command.contratoId())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Caso de cobranza no encontrado: " + command.contratoId())))
                .flatMap(caso -> {
                    int cuotasMarcadas = CuotaAplicador.aplicar(
                            caso.getCronograma(),
                            command.monto(),
                            command.fechaPago(),
                            command.numeroCuota());

                    log.info("[PAGO-MANUAL] Directo | contratoId={} cuotas={} monto={}",
                            command.contratoId(), cuotasMarcadas, command.monto());

                    double saldoAnterior = caso.getSaldoActual() != null ? caso.getSaldoActual() : 0.0;
                    double saldoNuevo    = saldoAnterior - command.monto();

                    caso.setSaldoActual(saldoNuevo);
                    caso.setTotalPagado((caso.getTotalPagado() != null ? caso.getTotalPagado() : 0.0) + command.monto());
                    caso.setUltimaGestion(new Date());
                    caso.setUltimaGestionResumen("Pago manual: S/ " + String.format("%.2f", command.monto()));
                    caso.setActualizadoEn(new Date());
                    caso.setActualizadoPor(command.registradoPor());

                    MovimientoDocument movimiento = MovimientoDocument.builder()
                            .id(UUID.randomUUID().toString())
                            .contratoId(command.contratoId())
                            .tipo("PAGO_MANUAL")
                            .monto(-command.monto())
                            .saldoAnterior(saldoAnterior)
                            .saldoNuevo(saldoNuevo)
                            .descripcion(command.observaciones() != null
                                    ? command.observaciones()
                                    : "Pago manual registrado por " + command.registradoPorNombre())
                            .cuotaNumero(command.numeroCuota())
                            .autorizadoPor(command.registradoPor())
                            .creadoEn(new Date())
                            .build();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("monto",         command.monto());
                    payload.put("fechaPago",      command.fechaPago());
                    payload.put("cuotasMarcadas", cuotasMarcadas);
                    payload.put("saldoAnterior",  saldoAnterior);
                    payload.put("saldoNuevo",     saldoNuevo);
                    payload.put("fuente",         command.fuente() != null ? command.fuente() : "ADMIN_MANUAL");
                    if (command.observaciones() != null) payload.put("observaciones", command.observaciones());
                    if (command.numeroCuota()   != null) payload.put("numeroCuota",   command.numeroCuota());

                    EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                            .contratoId(command.contratoId())
                            .tipo("PAGO_MANUAL_REGISTRADO")
                            .payload(payload)
                            .usuarioId(command.registradoPor())
                            .usuarioNombre(command.registradoPorNombre())
                            .automatico(false)
                            .creadoEn(new Date())
                            .build();

                    int cuotas = cuotasMarcadas;
                    return movimientoPort.append(command.contratoId(), movimiento)
                            .then(casoPort.save(caso))
                            .then(eventoPort.append(command.contratoId(), evento))
                            .thenReturn(new Result(command.contratoId(), saldoNuevo, cuotas, null, false));
                });
    }

    // ─── Ruta B: con imagen — flujo normal de vouchers ───────────────────────

    private Mono<Result> registrarConVoucher(RegistrarPagoManualCommand command) {
        return casoPort.findById(command.contratoId())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Caso de cobranza no encontrado: " + command.contratoId())))
                .flatMap(caso -> {
                    double saldoActual    = caso.getSaldoActual() != null ? caso.getSaldoActual() : 0.0;
                    Double montoEsperado  = CuotaAplicador.montoProximaCuota(caso.getCronograma());

                    // Los datos ingresados por el agente se almacenan como hints OCR
                    // AprobarVoucherUseCase los usa para fechaPago y monto al aprobar
                    OcrResultadoDocument ocrHint = OcrResultadoDocument.builder()
                            .monto(command.monto())
                            .fecha(command.fechaPago())
                            .procesador("MANUAL")
                            .confianza(1.0)
                            .build();

                    RecibirVoucherCommand recibirCmd = new RecibirVoucherCommand(
                            command.contratoId(),
                            caso.getStoreId(),
                            command.imagenPath(),
                            null,
                            command.monto(),
                            montoEsperado,
                            ocrHint,
                            command.registradoPor(),
                            "PAGO_MANUAL",
                            null
                    );

                    log.info("[PAGO-MANUAL] Con voucher | contratoId={} monto={} imagenPath={}",
                            command.contratoId(), command.monto(), command.imagenPath());

                    return recibirVoucherUseCase.ejecutar(recibirCmd)
                            .map(voucherId -> new Result(
                                    command.contratoId(), saldoActual, 0, voucherId, true));
                });
    }
}
