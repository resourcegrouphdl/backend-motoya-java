package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.ExportarComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.ReporteComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.ReporteEgresosUseCase;
import com.motoyav2.finanzas.application.port.in.ReportePagosTiendasUseCase;
import com.motoyav2.finanzas.application.port.out.ComisionPort;
import com.motoyav2.finanzas.application.port.out.CuentaPorPagarPort;
import com.motoyav2.finanzas.application.port.out.FacturaPort;
import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import com.motoyav2.finanzas.domain.model.CuotaCuenta;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService implements
        ExportarComisionesUseCase,
        ReportePagosTiendasUseCase,
        ReporteComisionesUseCase,
        ReporteEgresosUseCase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ComisionPort comisionPort;
    private final FacturaPort facturaPort;
    private final CuentaPorPagarPort cuentaPorPagarPort;

    // ─── 1. CSV de comisiones ──────────────────────────────────────────────────

    @Override
    public Mono<byte[]> ejecutar(String tiendaId, LocalDate fechaInicio, LocalDate fechaFin) {
        return comisionPort.findAll(tiendaId, fechaInicio, fechaFin)
                .collectList()
                .map(this::buildComisionesCsv);
    }

    private byte[] buildComisionesCsv(List<ComisionVendedor> lista) {
        StringBuilder sb = new StringBuilder();
        sb.append("vendedorNombre,tiendaNombre,ventasCount,montoTotal,comisionPorcentaje,montoComision,estado,fechaPago\n");
        for (ComisionVendedor c : lista) {
            sb.append(escapeCsv(c.getVendedorNombre())).append(',');
            sb.append(escapeCsv(c.getTiendaNombre())).append(',');
            sb.append(c.getTotalVentas()).append(',');
            sb.append(','); // montoTotal de ventas: no disponible en el modelo
            sb.append(','); // comisionPorcentaje: no disponible en el modelo
            sb.append(c.getMontoComision() != null ? c.getMontoComision().toPlainString() : "").append(',');
            sb.append(c.getEstado() != null ? c.getEstado().name() : "").append(',');
            sb.append(c.getPagadoEn() != null ? c.getPagadoEn().toLocalDate().format(DATE_FMT) : "").append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ─── 2. Reporte pagos por tienda ──────────────────────────────────────────

    @Override
    public Mono<byte[]> generarReportePagosTiendas(LocalDate fechaInicio, LocalDate fechaFin, String formato) {
        FiltrosFacturaRequest filtros = new FiltrosFacturaRequest();
        filtros.setFechaDesde(fechaInicio);
        filtros.setFechaHasta(fechaFin);
        return facturaPort.findAll(filtros)
                .collectList()
                .flatMap(facturas -> Mono.fromCallable(() -> buildPagosTiendas(facturas, formato))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private byte[] buildPagosTiendas(List<Factura> facturas, String formato) {
        record FilaTienda(String tiendaNombre, long totalFacturas, BigDecimal montoTotal,
                          BigDecimal montoPagado, BigDecimal saldoPendiente,
                          long facturasPendientes, long facturasVencidas) {}

        Map<String, List<Factura>> porTienda = facturas.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getTiendaNombre() != null ? f.getTiendaNombre() : "Sin tienda",
                        LinkedHashMap::new, Collectors.toList()));

        List<FilaTienda> filas = porTienda.entrySet().stream().map(e -> {
            List<Factura> fs = e.getValue();
            BigDecimal total = fs.stream().map(Factura::getMontoTotal).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pagado = fs.stream()
                    .flatMap(f -> f.getPagos() != null ? f.getPagos().stream() : java.util.stream.Stream.empty())
                    .filter(p -> EstadoPago.PAGADO == p.getEstado())
                    .map(PagoFactura::getMonto).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long pendientes = fs.stream().filter(f ->
                    f.getEstado() == EstadoPago.PENDIENTE || f.getEstado() == EstadoPago.PROXIMO_VENCER).count();
            long vencidas = fs.stream().filter(f -> f.getEstado() == EstadoPago.VENCIDO).count();
            return new FilaTienda(e.getKey(), fs.size(), total, pagado, total.subtract(pagado), pendientes, vencidas);
        }).toList();

        String[] headers = {"Tienda", "Total Facturas", "Monto Total", "Monto Pagado",
                "Saldo Pendiente", "Facturas Pendientes", "Facturas Vencidas"};

        if ("pdf".equalsIgnoreCase(formato)) {
            StringBuilder html = new StringBuilder(htmlHeader("Reporte Pagos por Tienda"));
            html.append("<table><thead><tr>");
            for (String h : headers) html.append("<th>").append(h).append("</th>");
            html.append("</tr></thead><tbody>");
            for (FilaTienda f : filas) {
                html.append("<tr>")
                        .append(td(f.tiendaNombre())).append(td(f.totalFacturas()))
                        .append(td(fmt(f.montoTotal()))).append(td(fmt(f.montoPagado())))
                        .append(td(fmt(f.saldoPendiente()))).append(td(f.facturasPendientes()))
                        .append(td(f.facturasVencidas())).append("</tr>");
            }
            html.append("</tbody></table></body></html>");
            return renderPdf(html.toString());
        } else {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Pagos Tiendas");
                CellStyle hs = headerStyle(wb);
                Row hRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) cell(hRow, i, headers[i], hs);
                int ri = 1;
                for (FilaTienda f : filas) {
                    Row row = sheet.createRow(ri++);
                    cell(row, 0, f.tiendaNombre());
                    cell(row, 1, f.totalFacturas());
                    cell(row, 2, f.montoTotal());
                    cell(row, 3, f.montoPagado());
                    cell(row, 4, f.saldoPendiente());
                    cell(row, 5, f.facturasPendientes());
                    cell(row, 6, f.facturasVencidas());
                }
                autoSize(sheet, headers.length);
                return toBytes(wb);
            } catch (Exception ex) {
                throw new RuntimeException("Error generando Excel pagos-tiendas", ex);
            }
        }
    }

    // ─── 3. Reporte comisiones (agrupado por tienda) ──────────────────────────

    @Override
    public Mono<byte[]> generarReporteComisiones(LocalDate fechaInicio, LocalDate fechaFin, String formato) {
        return comisionPort.findAll(null, fechaInicio, fechaFin)
                .collectList()
                .flatMap(list -> Mono.fromCallable(() -> buildComisionesReporte(list, formato))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private byte[] buildComisionesReporte(List<ComisionVendedor> lista, String formato) {
        Map<String, List<ComisionVendedor>> porTienda = lista.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getTiendaNombre() != null ? c.getTiendaNombre() : "Sin tienda",
                        LinkedHashMap::new, Collectors.toList()));

        String[] headers = {"Tienda", "Vendedor", "Ventas", "Monto Comisión", "Estado", "Fecha Pago"};

        if ("pdf".equalsIgnoreCase(formato)) {
            StringBuilder html = new StringBuilder(htmlHeader("Reporte de Comisiones"));
            html.append("<table><thead><tr>");
            for (String h : headers) html.append("<th>").append(h).append("</th>");
            html.append("</tr></thead><tbody>");
            for (Map.Entry<String, List<ComisionVendedor>> e : porTienda.entrySet()) {
                for (ComisionVendedor c : e.getValue()) {
                    html.append("<tr>").append(td(e.getKey())).append(td(c.getVendedorNombre()))
                            .append(td(c.getTotalVentas())).append(td(fmt(c.getMontoComision())))
                            .append(td(c.getEstado() != null ? c.getEstado().name() : ""))
                            .append(td(c.getPagadoEn() != null ? c.getPagadoEn().toLocalDate().format(DATE_FMT) : ""))
                            .append("</tr>");
                }
                BigDecimal subtotal = subtotalComisiones(e.getValue());
                html.append("<tr class=\"subtotal\"><td colspan=\"3\"><strong>Subtotal ").append(e.getKey())
                        .append("</strong></td><td><strong>").append(fmt(subtotal))
                        .append("</strong></td><td colspan=\"2\"></td></tr>");
            }
            html.append("</tbody></table></body></html>");
            return renderPdf(html.toString());
        } else {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Comisiones");
                CellStyle hs = headerStyle(wb);
                CellStyle ss = subtotalStyle(wb);
                Row hRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) cell(hRow, i, headers[i], hs);
                int ri = 1;
                for (Map.Entry<String, List<ComisionVendedor>> e : porTienda.entrySet()) {
                    for (ComisionVendedor c : e.getValue()) {
                        Row row = sheet.createRow(ri++);
                        cell(row, 0, e.getKey());
                        cell(row, 1, c.getVendedorNombre());
                        cell(row, 2, (long) c.getTotalVentas());
                        cell(row, 3, c.getMontoComision());
                        cell(row, 4, c.getEstado() != null ? c.getEstado().name() : "");
                        cell(row, 5, c.getPagadoEn() != null ? c.getPagadoEn().toLocalDate().format(DATE_FMT) : "");
                    }
                    Row stRow = sheet.createRow(ri++);
                    cell(stRow, 0, "Subtotal " + e.getKey(), ss);
                    cell(stRow, 3, subtotalComisiones(e.getValue()), ss);
                }
                autoSize(sheet, headers.length);
                return toBytes(wb);
            } catch (Exception ex) {
                throw new RuntimeException("Error generando Excel comisiones", ex);
            }
        }
    }

    private BigDecimal subtotalComisiones(List<ComisionVendedor> list) {
        return list.stream().map(ComisionVendedor::getMontoComision).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ─── 4. Reporte egresos (CuentaPorPagar) ──────────────────────────────────

    @Override
    public Mono<byte[]> generarReporteEgresos(LocalDate fechaInicio, LocalDate fechaFin, String formato) {
        return cuentaPorPagarPort.findAll(null, null)
                .filter(c -> enRango(c.getFechaVencimiento(), fechaInicio, fechaFin))
                .collectList()
                .flatMap(list -> Mono.fromCallable(() -> buildEgresosReporte(list, formato))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private byte[] buildEgresosReporte(List<CuentaPorPagar> lista, String formato) {
        String[] headers = {"Proveedor", "Concepto", "Monto", "Fecha Vencimiento", "Estado", "Fecha Pago"};

        if ("pdf".equalsIgnoreCase(formato)) {
            StringBuilder html = new StringBuilder(htmlHeader("Reporte de Egresos"));
            html.append("<table><thead><tr>");
            for (String h : headers) html.append("<th>").append(h).append("</th>");
            html.append("</tr></thead><tbody>");
            for (CuentaPorPagar c : lista) {
                html.append("<tr>").append(td(c.getProveedor())).append(td(c.getDescripcion()))
                        .append(td(fmt(c.getMontoTotal())))
                        .append(td(c.getFechaVencimiento() != null ? c.getFechaVencimiento().format(DATE_FMT) : ""))
                        .append(td(c.getEstado() != null ? c.getEstado().name() : ""))
                        .append(td(fechaPagoCuenta(c))).append("</tr>");
            }
            html.append("</tbody></table></body></html>");
            return renderPdf(html.toString());
        } else {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Egresos");
                CellStyle hs = headerStyle(wb);
                Row hRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) cell(hRow, i, headers[i], hs);
                int ri = 1;
                for (CuentaPorPagar c : lista) {
                    Row row = sheet.createRow(ri++);
                    cell(row, 0, c.getProveedor());
                    cell(row, 1, c.getDescripcion());
                    cell(row, 2, c.getMontoTotal());
                    cell(row, 3, c.getFechaVencimiento() != null ? c.getFechaVencimiento().format(DATE_FMT) : "");
                    cell(row, 4, c.getEstado() != null ? c.getEstado().name() : "");
                    cell(row, 5, fechaPagoCuenta(c));
                }
                autoSize(sheet, headers.length);
                return toBytes(wb);
            } catch (Exception ex) {
                throw new RuntimeException("Error generando Excel egresos", ex);
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String fechaPagoCuenta(CuentaPorPagar c) {
        if (c.getCuotas() == null) return "";
        return c.getCuotas().stream()
                .filter(cu -> cu.getEstado() == EstadoCuenta.PAGADO && cu.getFechaPago() != null)
                .max(Comparator.comparing(CuotaCuenta::getFechaPago))
                .map(cu -> cu.getFechaPago().format(DATE_FMT))
                .orElse("");
    }

    private boolean enRango(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) return true;
        if (desde != null && fecha.isBefore(desde)) return false;
        if (hasta != null && fecha.isAfter(hasta)) return false;
        return true;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String fmt(BigDecimal v) {
        return v != null ? v.toPlainString() : "0";
    }

    // ─── PDF ─────────────────────────────────────────────────────────────────

    private byte[] renderPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF", e);
        }
    }

    private String htmlHeader(String titulo) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8"/>
                <style>
                  body { font-family: Arial, sans-serif; font-size: 10px; margin: 20px; }
                  h1 { font-size: 14px; color: #333; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 10px; }
                  th { background-color: #2c3e50; color: white; padding: 6px; text-align: left; }
                  td { padding: 5px; border-bottom: 1px solid #ddd; }
                  tr:nth-child(even) { background-color: #f9f9f9; }
                  .subtotal td { background-color: #eaf0fb; font-weight: bold; }
                </style>
                </head>
                <body>
                <h1>%s</h1>
                """.formatted(titulo);
    }

    private String td(Object value) {
        return "<td>" + (value != null ? value.toString() : "") + "</td>";
    }

    // ─── Excel ───────────────────────────────────────────────────────────────

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle subtotalStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void cell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private void cell(Row row, int col, long value) {
        row.createCell(col).setCellValue(value);
    }

    private void cell(Row row, int col, BigDecimal value) {
        row.createCell(col).setCellValue(value != null ? value.doubleValue() : 0.0);
    }

    private void cell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value.doubleValue() : 0.0);
        c.setCellStyle(style);
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
    }

    private byte[] toBytes(Workbook wb) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }
}
