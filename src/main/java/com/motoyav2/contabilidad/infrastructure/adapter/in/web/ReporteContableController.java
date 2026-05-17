package com.motoyav2.contabilidad.infrastructure.adapter.in.web;

import com.motoyav2.contabilidad.domain.model.ReporteLiquidacionResponse;
import com.motoyav2.contabilidad.domain.port.in.GenerarExcelContratosUseCase;
import com.motoyav2.contabilidad.domain.port.in.GenerarLiquidacionComisionesUseCase;
import com.motoyav2.contabilidad.domain.port.in.GenerarPdfLiquidacionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/contabilidad/reportes")
@RequiredArgsConstructor
public class ReporteContableController {

    private static final String EXCEL_CT = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CT   = "application/pdf";

    private final GenerarLiquidacionComisionesUseCase liquidacionUseCase;
    private final GenerarPdfLiquidacionUseCase         pdfUseCase;
    private final GenerarExcelContratosUseCase         excelUseCase;

    /**
     * GET /api/v1/contabilidad/reportes/comisiones-vendedor
     * Devuelve el reporte agrupado en JSON para previsualización en el frontend.
     */
    @GetMapping("/comisiones-vendedor")
    public Mono<ReporteLiquidacionResponse> getLiquidacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/reportes/comisiones-vendedor desde={} hasta={} tiendaId={}", d, h, tiendaId);
        return liquidacionUseCase.generar(d, h, tiendaId);
    }

    /**
     * GET /api/v1/contabilidad/reportes/comisiones-vendedor/pdf
     * Descarga el PDF de liquidación de comisiones por vendedor.
     */
    @GetMapping("/comisiones-vendedor/pdf")
    public Mono<ResponseEntity<byte[]>> getPdfLiquidacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/reportes/comisiones-vendedor/pdf desde={} hasta={} tiendaId={}", d, h, tiendaId);

        String filename = "liquidacion-comisiones-" + d + "-al-" + h + ".pdf";
        return pdfUseCase.generarPdf(d, h, tiendaId)
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Type", PDF_CT)
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .body(bytes));
    }

    /**
     * GET /api/v1/contabilidad/reportes/contratos
     * Descarga el Excel de contratos cerrados en el período.
     */
    @GetMapping("/contratos")
    public Mono<ResponseEntity<byte[]>> getExcelContratos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tiendaId) {

        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        log.info("GET /contabilidad/reportes/contratos desde={} hasta={} tiendaId={}", d, h, tiendaId);

        String filename = "contratos-cerrados-" + d + "-al-" + h + ".xlsx";
        return excelUseCase.generarExcel(d, h, tiendaId)
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Type", EXCEL_CT)
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .body(bytes));
    }
}
