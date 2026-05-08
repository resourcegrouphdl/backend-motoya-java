package com.motoyav2.contabilidad.infrastructure.adapter.in.web;

import com.motoyav2.contabilidad.domain.model.*;
import com.motoyav2.contabilidad.domain.port.in.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/contabilidad")
@RequiredArgsConstructor
public class ContabilidadController {

    private final ConsultarComprobantesUseCase consultarComprobantesUseCase;
    private final ConsultarIgvUseCase consultarIgvUseCase;
    private final ConsultarCarteraActivaUseCase consultarCarteraActivaUseCase;
    private final ConsultarAgingUseCase consultarAgingUseCase;
    private final ConsultarRecaudacionUseCase consultarRecaudacionUseCase;
    private final ConsultarDiscrepanciasUseCase consultarDiscrepanciasUseCase;
    private final ConsultarConcentracionBancariaUseCase consultarConcentracionBancariaUseCase;
    private final ConsultarFlujoCajaUseCase consultarFlujoCajaUseCase;
    private final ConsultarDesglosePeriodoUseCase consultarDesglosePeriodoUseCase;
    private final ConsultarUtilidadUseCase consultarUtilidadUseCase;
    private final SincronizarContabilidadUseCase sincronizarContabilidadUseCase;

    /**
     * GET /api/v1/contabilidad/comprobantes
     * Lista los comprobantes SUNAT emitidos en el período indicado.
     *
     * @param desde    fecha inicio (inclusiva), formato ISO 8601 (yyyy-MM-dd)
     * @param hasta    fecha fin (inclusiva), formato ISO 8601 (yyyy-MM-dd)
     * @param tiendaId filtro opcional por tienda/sucursal
     * @param tipo     filtro opcional: BOLETA | FACTURA
     */
    @GetMapping("/comprobantes")
    public Flux<ComprobanteContable> getComprobantes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) String tipo) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/comprobantes desde={} hasta={} tiendaId={} tipo={}", d, h, tiendaId, tipo);
        return consultarComprobantesUseCase.ejecutar(d, h, tiendaId, tipo);
    }

    /**
     * GET /api/v1/contabilidad/igv
     * Resumen de IGV acumulado en el período.
     */
    @GetMapping("/igv")
    public Mono<ResumenIgv> getIgv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/igv desde={} hasta={} tiendaId={}", d, h, tiendaId);
        return consultarIgvUseCase.ejecutar(d, h, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/cartera
     * Foto consolidada de la cartera activa.
     */
    @GetMapping("/cartera")
    public Mono<SnapshotCartera> getCartera(
            @RequestParam(required = false) String tiendaId) {

        log.info("GET /contabilidad/cartera tiendaId={}", tiendaId);
        return consultarCarteraActivaUseCase.ejecutar(tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/aging
     * Distribución de la cartera por tramos de mora (aging).
     *
     * @param fechaCorte fecha de corte para calcular mora; default hoy
     */
    @GetMapping("/aging")
    public Flux<BucketMora> getAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @RequestParam(required = false) String tiendaId) {

        LocalDate corte = fechaCorte != null ? fechaCorte : LocalDate.now();
        log.info("GET /contabilidad/aging fechaCorte={} tiendaId={}", corte, tiendaId);
        return consultarAgingUseCase.ejecutar(corte, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/recaudacion
     * Resumen de recaudación del período, con puntos agrupados por DIA/SEMANA/MES.
     *
     * @param agruparPor DIA | SEMANA | MES (default MES)
     */
    @GetMapping("/recaudacion")
    public Mono<ResumenRecaudacion> getRecaudacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false, defaultValue = "MES") String agruparPor) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/recaudacion desde={} hasta={} tiendaId={} agruparPor={}", d, h, tiendaId, agruparPor);
        return consultarRecaudacionUseCase.ejecutar(d, h, tiendaId, agruparPor);
    }

    /**
     * GET /api/v1/contabilidad/discrepancias
     * Vouchers con diferencia significativa entre monto OCR y monto esperado.
     */
    @GetMapping("/discrepancias")
    public Flux<DiscrepanciaVoucher> getDiscrepancias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/discrepancias desde={} hasta={} tiendaId={}", d, h, tiendaId);
        return consultarDiscrepanciasUseCase.ejecutar(d, h, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/bancos
     * Distribución de pagos recibidos por banco (concentración bancaria).
     */
    @GetMapping("/bancos")
    public Flux<ConcentracionBancaria> getConcentracionBancaria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/bancos desde={} hasta={} tiendaId={}", d, h, tiendaId);
        return consultarConcentracionBancariaUseCase.ejecutar(d, h, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/flujo-caja
     * Proyección del flujo de caja esperado en los próximos N meses.
     *
     * @param meses número de meses a proyectar (default 3)
     */
    @GetMapping("/flujo-caja")
    public Flux<PuntoRecaudacion> getFlujoCaja(
            @RequestParam(required = false, defaultValue = "3") int meses,
            @RequestParam(required = false) String tiendaId) {

        log.info("GET /contabilidad/flujo-caja meses={} tiendaId={}", meses, tiendaId);
        return consultarFlujoCajaUseCase.ejecutar(meses, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/desglose
     * Desglose de ingresos por quincena: capital, interés, costos tienda, comisiones y utilidad.
     * Lee desde el ledger contabilidad_movimientos (generado por el scheduler).
     */
    @GetMapping("/desglose")
    public Flux<DesglosePeriodo> getDesglose(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().minusMonths(3).withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/desglose desde={} hasta={} tiendaId={}", d, h, tiendaId);
        return consultarDesglosePeriodoUseCase.ejecutar(d, h, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/utilidad
     * Resumen ejecutivo de utilidad: ingresos, costos y margen neto del período.
     */
    @GetMapping("/utilidad")
    public Mono<UtilidadPeriodo> getUtilidad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/utilidad desde={} hasta={} tiendaId={}", d, h, tiendaId);
        return consultarUtilidadUseCase.ejecutar(d, h, tiendaId);
    }

    /**
     * POST /api/v1/contabilidad/admin/sincronizar
     * Dispara el backfill histórico manual. Solo para administración.
     * El scheduler automático corre cada 6 horas.
     */
    @PostMapping("/admin/sincronizar")
    public Mono<ResponseEntity<String>> sincronizarHistorico() {
        log.info("POST /contabilidad/admin/sincronizar — backfill histórico iniciado");
        return sincronizarContabilidadUseCase.sincronizarHistorico()
                .map(total -> ResponseEntity.ok(
                        "Sincronización completada. Movimientos procesados: " + total))
                .onErrorReturn(ResponseEntity.internalServerError()
                        .body("Error durante la sincronización"));
    }
}
