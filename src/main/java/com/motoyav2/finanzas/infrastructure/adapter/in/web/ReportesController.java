package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.GenerarPaqueteContabilidadUseCase;
import com.motoyav2.finanzas.application.port.in.ReporteComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.ReporteEgresosUseCase;
import com.motoyav2.finanzas.application.port.in.ReportePagosTiendasUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportesController {

    private static final String EXCEL_CT = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CT   = "application/pdf";
    private static final String ZIP_CT   = "application/zip";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportePagosTiendasUseCase        reportePagosTiendas;
    private final ReporteComisionesUseCase           reporteComisiones;
    private final ReporteEgresosUseCase              reporteEgresos;
    private final GenerarPaqueteContabilidadUseCase  paqueteContabilidad;

    @GetMapping("/pagos-tiendas")
    public Mono<ResponseEntity<byte[]>> reportePagosTiendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaFin,
            @RequestParam(defaultValue = "excel") String formato) {

        return reportePagosTiendas.generarReportePagosTiendas(fechaInicio, fechaFin, formato)
                .map(bytes -> fileResponse(bytes, formato, "pagos-tiendas"));
    }

    @GetMapping("/comisiones")
    public Mono<ResponseEntity<byte[]>> reporteComisiones(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaFin,
            @RequestParam(defaultValue = "excel") String formato) {

        return reporteComisiones.generarReporteComisiones(fechaInicio, fechaFin, formato)
                .map(bytes -> fileResponse(bytes, formato, "comisiones"));
    }

    @GetMapping("/egresos-mes")
    public Mono<ResponseEntity<byte[]>> reporteEgresos(
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaFin,
            @RequestParam(defaultValue = "excel") String formato) {

        return reporteEgresos.generarReporteEgresos(fechaInicio, fechaFin, formato)
                .map(bytes -> fileResponse(bytes, formato, "egresos-mes"));
    }

    @GetMapping("/paquete-contabilidad")
    public Mono<ResponseEntity<byte[]>> paqueteContabilidad(
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate fechaFin) {

        String nombre = "evidencias_" + fechaInicio.format(FILE_DATE) + "_" + fechaFin.format(FILE_DATE) + ".zip";
        return paqueteContabilidad.ejecutar(fechaInicio, fechaFin)
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Type", ZIP_CT)
                        .header("Content-Disposition", "attachment; filename=\"" + nombre + "\"")
                        .body(bytes))
                .onErrorResume(IllegalArgumentException.class, ex ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .<byte[]>body(ex.getMessage().getBytes())));
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String formato, String nombre) {
        boolean isPdf = "pdf".equalsIgnoreCase(formato);
        String contentType = isPdf ? PDF_CT : EXCEL_CT;
        String extension   = isPdf ? ".pdf" : ".xlsx";
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=\"reporte-" + nombre + extension + "\"")
                .body(bytes);
    }
}
