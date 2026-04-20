package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.RegistrarPagoManualUseCase;
import com.motoyav2.cobranza.application.port.in.command.RegistrarPagoManualCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MovimientoPort;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
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
 * Casos de uso:
 *   - Clientes migrados con pagos realizados antes del sistema
 *   - Pagos en efectivo sin comprobante digital
 *   - Corrección de registros
 *
 * Flujo:
 *   1. Carga el caso
 *   2. Aplica CuotaAplicador (marcando cuotas como PAGADA)
 *   3. Crea Movimiento (tipo PAGO_MANUAL)
 *   4. Si hay imagen: crea VoucherDocument con estado APROBADO directamente
 *   5. Actualiza saldo y auditoría del caso
 *   6. Registra EventoCobranza de tipo PAGO_MANUAL_REGISTRADO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrarPagoManualService implements RegistrarPagoManualUseCase {

    private final CasoCobranzaPort  casoPort;
    private final MovimientoPort    movimientoPort;
    private final VoucherPort       voucherPort;
    private final EventoCobranzaPort eventoPort;

    @Override
    public Mono<Result> ejecutar(RegistrarPagoManualCommand command) {
        return casoPort.findById(command.contratoId())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Caso de cobranza no encontrado: " + command.contratoId())))
                .flatMap(caso -> {
                    // 1. Marcar cuotas según la lógica del CuotaAplicador
                    int cuotasMarcadas = CuotaAplicador.aplicar(
                            caso.getCronograma(),
                            command.monto(),
                            command.fechaPago(),
                            command.numeroCuota());

                    log.info("[PAGO-MANUAL] Cuotas marcadas | contratoId={} count={} fechaPago={}",
                            command.contratoId(), cuotasMarcadas, command.fechaPago());

                    // 2. Actualizar saldo del caso
                    double saldoAnterior = caso.getSaldoActual() != null ? caso.getSaldoActual() : 0.0;
                    double saldoNuevo    = saldoAnterior - command.monto();

                    caso.setSaldoActual(saldoNuevo);
                    caso.setTotalPagado((caso.getTotalPagado() != null ? caso.getTotalPagado() : 0.0)
                            + command.monto());
                    caso.setUltimaGestion(new Date());
                    caso.setUltimaGestionResumen("Pago manual: S/ " + String.format("%.2f", command.monto()));
                    caso.setActualizadoEn(new Date());
                    caso.setActualizadoPor(command.registradoPor());

                    // 3. Movimiento
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

                    // 4. Voucher opcional (si tiene imagen del comprobante)
                    Mono<String> voucherIdMono = Mono.just("");
                    if (command.imagenPath() != null && !command.imagenPath().isBlank()) {
                        VoucherDocument voucher = VoucherDocument.builder()
                                .id(UUID.randomUUID().toString())
                                .contratoId(command.contratoId())
                                .storeId(caso.getStoreId())
                                .estado("APROBADO")
                                .imagenPath(command.imagenPath())
                                .montoDetectado(command.monto())
                                .montoEsperado(CuotaAplicador.montoProximaCuota(caso.getCronograma()))
                                .aprobadoPor(command.registradoPor())
                                .aprobadoPorNombre(command.registradoPorNombre())
                                .procesadoEn(new Date())
                                .creadoEn(new Date())
                                .creadoPor(command.registradoPor())
                                .build();
                        voucherIdMono = voucherPort.save(voucher).map(VoucherDocument::getId);
                    }

                    // 5. Evento de auditoría
                    Map<String, Object> payloadEvento = new HashMap<>();
                    payloadEvento.put("monto",         command.monto());
                    payloadEvento.put("fechaPago",      command.fechaPago());
                    payloadEvento.put("cuotasMarcadas", cuotasMarcadas);
                    payloadEvento.put("saldoAnterior",  saldoAnterior);
                    payloadEvento.put("saldoNuevo",     saldoNuevo);
                    payloadEvento.put("fuente",         command.fuente() != null ? command.fuente() : "ADMIN_MANUAL");
                    if (command.observaciones() != null) payloadEvento.put("observaciones", command.observaciones());
                    if (command.numeroCuota()   != null) payloadEvento.put("numeroCuota",   command.numeroCuota());

                    EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                            .contratoId(command.contratoId())
                            .tipo("PAGO_MANUAL_REGISTRADO")
                            .payload(payloadEvento)
                            .usuarioId(command.registradoPor())
                            .usuarioNombre(command.registradoPorNombre())
                            .automatico(false)
                            .creadoEn(new Date())
                            .build();

                    int cuotasCapturadas = cuotasMarcadas;
                    return voucherIdMono
                            .flatMap(vid ->
                                    movimientoPort.append(command.contratoId(), movimiento)
                                            .then(casoPort.save(caso))
                                            .then(eventoPort.append(command.contratoId(), evento))
                                            .thenReturn(new Result(
                                                    command.contratoId(),
                                                    saldoNuevo,
                                                    cuotasCapturadas,
                                                    vid.isBlank() ? null : vid)));
                });
    }
}
