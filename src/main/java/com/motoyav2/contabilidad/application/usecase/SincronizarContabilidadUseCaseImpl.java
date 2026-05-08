package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.*;
import com.motoyav2.contabilidad.domain.port.in.SincronizarContabilidadUseCase;
import com.motoyav2.contabilidad.domain.port.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class SincronizarContabilidadUseCaseImpl implements SincronizarContabilidadUseCase {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final ContabilidadCuotaPort cuotaPort;
    private final MovimientoContablePort movimientoPort;
    private final ContratoDataPort contratoDataPort;
    private final VoucherPagoPort voucherPagoPort;
    private final FacturaTiendaPort facturaTiendaPort;
    private final ComisionPagadaPort comisionPagadaPort;

    @Override
    public Mono<Integer> sincronizarIncremental() {
        log.info("[SYNC-CONTABILIDAD] Iniciando sincronización incremental");
        return ejecutarSincronizacion("INCREMENTAL");
    }

    @Override
    public Mono<Integer> sincronizarHistorico() {
        log.info("[SYNC-CONTABILIDAD] Iniciando backfill histórico completo");
        return ejecutarSincronizacion("HISTORICO");
    }

    private Mono<Integer> ejecutarSincronizacion(String modo) {
        AtomicInteger contador = new AtomicInteger(0);

        return sincronizarCuotasDeContratos()
                .then(procesarVouchers(contador))
                .then(procesarFacturasTienda(contador))
                .then(procesarComisiones(contador))
                .thenReturn(contador.get())
                .doOnSuccess(total ->
                        log.info("[SYNC-CONTABILIDAD] {} completado | movimientos procesados={}", modo, total))
                .onErrorResume(e -> {
                    log.error("[SYNC-CONTABILIDAD] Error en {}: {}", modo, e.getMessage(), e);
                    return Mono.just(contador.get());
                });
    }

    /**
     * Recalcula y persiste el desglose capital/interés de cada contrato
     * en contabilidad_cuotas. Solo crea si no existe (idempotente).
     */
    private Mono<Void> sincronizarCuotasDeContratos() {
        return contratoDataPort.findTodos()
                .flatMap(contrato ->
                        cuotaPort.findByContratoId(contrato.contratoId())
                                .switchIfEmpty(Mono.defer(() -> calcularYGuardarCuota(contrato)))
                                .then()
                )
                .then()
                .doOnSuccess(v -> log.info("[SYNC-CONTABILIDAD] Cuotas de contratos sincronizadas"));
    }

    private Mono<ContabilidadCuota> calcularYGuardarCuota(ContratoData contrato) {
        double tasa         = contrato.tasaEfectiva();
        double monto        = contrato.montoFinanciado();
        int    quincenas    = contrato.numeroCuotas();

        if (quincenas <= 0 || monto <= 0) return Mono.empty();

        double interesTotal  = monto * tasa;
        double capitalTotal  = monto;
        double interesQ      = interesTotal / quincenas;
        double capitalQ      = capitalTotal / quincenas;

        List<DesgloseCuota> cuotas = new ArrayList<>();
        for (int i = 1; i <= quincenas; i++) {
            cuotas.add(DesgloseCuota.builder()
                    .numero(i)
                    .fechaVencimiento(null)
                    .montoTotal(round2(capitalQ + interesQ))
                    .montoCapital(round2(capitalQ))
                    .montoInteres(round2(interesQ))
                    .build());
        }

        ContabilidadCuota cuota = ContabilidadCuota.builder()
                .contratoId(contrato.contratoId())
                .tiendaId(contrato.tiendaId())
                .numeroCuotas(quincenas)
                .montoFinanciar(monto)
                .tasaInteres(tasa)
                .interesTotal(round2(interesTotal))
                .capitalTotal(round2(capitalTotal))
                .cuotas(cuotas)
                .calculadoEn(Instant.now())
                .build();

        return cuotaPort.save(cuota).thenReturn(cuota);
    }

    /**
     * Procesa vouchers APROBADO → INGRESO_CUOTA en el ledger.
     */
    private Mono<Void> procesarVouchers(AtomicInteger contador) {
        return voucherPagoPort.findTodosAprobados()
                .flatMap(voucher ->
                        movimientoPort.existsByReferenciaId(voucher.voucherId())
                                .filter(existe -> !existe)
                                .flatMap(__ -> cuotaPort.findByContratoId(voucher.contratoId())
                                        .switchIfEmpty(contratoDataPort.findById(voucher.contratoId())
                                                .flatMap(this::calcularYGuardarCuota))
                                        .flatMap(cuota -> {
                                            double capitalQ = cuota.getNumeroCuotas() > 0
                                                    ? cuota.getCapitalTotal() / cuota.getNumeroCuotas() : voucher.monto();
                                            double interesQ = cuota.getNumeroCuotas() > 0
                                                    ? cuota.getInteresTotal() / cuota.getNumeroCuotas() : 0.0;

                                            MovimientoContable mov = MovimientoContable.builder()
                                                    .id(voucher.voucherId())
                                                    .tipo(TipoMovimientoContable.INGRESO_CUOTA)
                                                    .contratoId(voucher.contratoId())
                                                    .tiendaId(voucher.tiendaId())
                                                    .referenciaId(voucher.voucherId())
                                                    .periodo(calcularPeriodo(voucher.creadoEn()))
                                                    .montoTotal(round2(voucher.monto()))
                                                    .montoCapital(round2(capitalQ))
                                                    .montoInteres(round2(interesQ))
                                                    .montoCosto(0.0)
                                                    .creadoEn(voucher.creadoEn() != null ? voucher.creadoEn() : Instant.now())
                                                    .build();

                                            return movimientoPort.save(mov)
                                                    .doOnSuccess(v -> contador.incrementAndGet());
                                        })
                                )
                )
                .then();
    }

    /**
     * Procesa pagos a tienda PAGADO → COSTO_TIENDA en el ledger.
     */
    private Mono<Void> procesarFacturasTienda(AtomicInteger contador) {
        return facturaTiendaPort.findTodosPagados()
                .flatMap(pago ->
                        movimientoPort.existsByReferenciaId(pago.referenciaId())
                                .filter(existe -> !existe)
                                .flatMap(__ -> {
                                    MovimientoContable mov = MovimientoContable.builder()
                                            .id(pago.referenciaId())
                                            .tipo(TipoMovimientoContable.COSTO_TIENDA)
                                            .contratoId(pago.contratoId())
                                            .tiendaId(pago.tiendaId())
                                            .referenciaId(pago.referenciaId())
                                            .periodo(calcularPeriodo(pago.fechaPago()))
                                            .montoTotal(0.0)
                                            .montoCapital(0.0)
                                            .montoInteres(0.0)
                                            .montoCosto(round2(pago.monto()))
                                            .creadoEn(pago.fechaPago() != null ? pago.fechaPago() : Instant.now())
                                            .build();
                                    return movimientoPort.save(mov)
                                            .doOnSuccess(v -> contador.incrementAndGet());
                                })
                )
                .then();
    }

    /**
     * Procesa comisiones pagadas → COSTO_COMISION en el ledger.
     */
    private Mono<Void> procesarComisiones(AtomicInteger contador) {
        return comisionPagadaPort.findTodosPagadas()
                .flatMap(comision ->
                        movimientoPort.existsByReferenciaId(comision.referenciaId())
                                .filter(existe -> !existe)
                                .flatMap(__ -> {
                                    MovimientoContable mov = MovimientoContable.builder()
                                            .id(comision.referenciaId())
                                            .tipo(TipoMovimientoContable.COSTO_COMISION)
                                            .contratoId("")
                                            .tiendaId(comision.tiendaId())
                                            .referenciaId(comision.referenciaId())
                                            .periodo(calcularPeriodo(comision.fechaPago()))
                                            .montoTotal(0.0)
                                            .montoCapital(0.0)
                                            .montoInteres(0.0)
                                            .montoCosto(round2(comision.monto()))
                                            .creadoEn(comision.fechaPago() != null ? comision.fechaPago() : Instant.now())
                                            .build();
                                    return movimientoPort.save(mov)
                                            .doOnSuccess(v -> contador.incrementAndGet());
                                })
                )
                .then();
    }

    /** Devuelve el primer día de la quincena correspondiente. */
    private String calcularPeriodo(Instant instant) {
        if (instant == null) return LocalDate.now(LIMA).withDayOfMonth(1).toString();
        LocalDate fecha = instant.atZone(LIMA).toLocalDate();
        LocalDate inicio = fecha.getDayOfMonth() <= 15
                ? fecha.withDayOfMonth(1)
                : fecha.withDayOfMonth(16);
        return inicio.toString();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
