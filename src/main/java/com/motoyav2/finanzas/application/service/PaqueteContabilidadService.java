package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.GenerarPaqueteContabilidadUseCase;
import com.motoyav2.finanzas.application.port.out.FacturaPort;
import com.motoyav2.finanzas.application.port.out.GcsDownloadPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaqueteContabilidadService implements GenerarPaqueteContabilidadUseCase {

    private static final int MAX_VOUCHERS = 150;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FacturaPort facturaPort;
    private final GcsDownloadPort gcsDownload;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Override
    public Mono<byte[]> ejecutar(LocalDate fechaInicio, LocalDate fechaFin) {
        return facturaPort.findAll(new FiltrosFacturaRequest())
                .collectList()
                .flatMap(todas -> {
                    List<Factura> relevantes = todas.stream()
                            .filter(f -> facturaEnPeriodo(f, fechaInicio, fechaFin))
                            .sorted(Comparator.comparing(Factura::getNumero,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();

                    long totalVouchers = relevantes.stream()
                            .flatMap(f -> f.getPagos() != null ? f.getPagos().stream() : java.util.stream.Stream.empty())
                            .filter(p -> p.getEstado() == EstadoPago.PAGADO && p.getVoucherGcsPath() != null)
                            .count();

                    if (totalVouchers > MAX_VOUCHERS) {
                        return Mono.error(new IllegalArgumentException(
                                "El período contiene " + totalVouchers + " vouchers (máx. " + MAX_VOUCHERS +
                                        "). Reduzca el rango de fechas."));
                    }

                    log.info("[PaqueteContabilidad] {} facturas por fecha de emisión · {} vouchers PAGADO — período {}/{}",
                            relevantes.size(), totalVouchers, fechaInicio, fechaFin);

                    return Mono.fromCallable(() -> buildZip(relevantes, fechaInicio, fechaFin))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    // ── ZIP ───────────────────────────────────────────────────────────────────

    private byte[] buildZip(List<Factura> facturas, LocalDate desde, LocalDate hasta) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {

            // 1. Excel resumen en la raíz del ZIP
            byte[] excel = buildExcel(facturas, desde, hasta);
            zip.putNextEntry(new ZipEntry("resumen.xlsx"));
            zip.write(excel);
            zip.closeEntry();

            // 2. Una sub-carpeta por factura con sus vouchers
            for (Factura factura : facturas) {
                if (factura.getPagos() == null) continue;

                String carpeta = sanitize(factura.getNumero() + "_" + factura.getTiendaNombre()) + "/";

                // Documento de la factura
                String gcsPathFactura = gcsPathDeUrl(factura.getUrlDocumentoFactura());
                if (gcsPathFactura != null) {
                    try {
                        byte[] bytes = gcsDownload.descargar(gcsPathFactura).block();
                        if (bytes != null) {
                            String ext = extensionDe(gcsPathFactura);
                            zip.putNextEntry(new ZipEntry(carpeta + "factura_" + sanitize(factura.getNumero()) + ext));
                            zip.write(bytes);
                            zip.closeEntry();
                        }
                    } catch (Exception e) {
                        log.warn("[PaqueteContabilidad] Factura omitida — path={} error={}", gcsPathFactura, e.getMessage());
                    }
                }

                for (PagoFactura pago : factura.getPagos()) {
                    if (pago.getEstado() != EstadoPago.PAGADO || pago.getVoucherGcsPath() == null) continue;

                    String ext = extensionDe(pago.getVoucherGcsPath());
                    String concepto = pago.getConcepto() != null ? pago.getConcepto().name() : "PAGO";
                    String fecha    = pago.getFechaPago() != null ? "_" + pago.getFechaPago().format(FILE_FMT) : "";
                    String entrada  = carpeta + "cuota" + pago.getNumero() + "_" + concepto + fecha + ext;

                    try {
                        byte[] bytes = gcsDownload.descargar(pago.getVoucherGcsPath()).block();
                        if (bytes != null) {
                            zip.putNextEntry(new ZipEntry(entrada));
                            zip.write(bytes);
                            zip.closeEntry();
                        }
                    } catch (Exception e) {
                        log.warn("[PaqueteContabilidad] Voucher omitido — path={} error={}", pago.getVoucherGcsPath(), e.getMessage());
                    }
                }
            }

            zip.finish();
            return baos.toByteArray();
        }
    }

    // ── Excel ─────────────────────────────────────────────────────────────────

    private byte[] buildExcel(List<Factura> facturas, LocalDate desde, LocalDate hasta) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Pagos a Tiendas");

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle subHeaderStyle = buildSubHeaderStyle(wb);

            // Título
            Row titulo = sheet.createRow(0);
            Cell tituloCell = titulo.createCell(0);
            tituloCell.setCellValue("Evidencias de Pagos a Tiendas — " +
                    (desde != null ? desde.format(DATE_FMT) : "") + " al " +
                    (hasta != null ? hasta.format(DATE_FMT) : ""));
            tituloCell.setCellStyle(buildTitleStyle(wb));

            // Cabeceras
            String[] headers = {
                    "N° Factura", "Tienda", "Cliente", "Moto",
                    "Monto Total", "N° Cuota", "Concepto",
                    "Monto Cuota", "Método Pago", "Fecha Prog.", "Fecha Pago",
                    "Voucher", "Doc AI - Monto", "Doc AI - Fecha", "Doc AI - Banco", "Doc AI - N° Doc"
            };
            Row hRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 3;
            for (Factura f : facturas) {
                if (f.getPagos() == null || f.getPagos().isEmpty()) continue;

                for (PagoFactura pago : f.getPagos()) {
                    Map<String, String> ai = pago.getDocumentAiCampos();
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(nvl(f.getNumero()));
                    row.createCell(1).setCellValue(nvl(f.getTiendaNombre()));
                    row.createCell(2).setCellValue(nvl(f.getClienteNombre()));
                    row.createCell(3).setCellValue(nvl(f.getMotoModelo()));
                    row.createCell(4).setCellValue(f.getMontoTotal() != null ? f.getMontoTotal().doubleValue() : 0);
                    row.createCell(5).setCellValue(pago.getNumero());
                    row.createCell(6).setCellValue(pago.getConcepto() != null ? pago.getConcepto().name() : "");
                    row.createCell(7).setCellValue(pago.getMonto() != null ? pago.getMonto().doubleValue() : 0);
                    row.createCell(8).setCellValue(pago.getMetodoPago() != null ? pago.getMetodoPago().name() : "");
                    row.createCell(9).setCellValue(pago.getFechaProgramada() != null ? pago.getFechaProgramada().format(DATE_FMT) : "");
                    row.createCell(10).setCellValue(pago.getFechaPago() != null ? pago.getFechaPago().format(DATE_FMT) : "");
                    row.createCell(11).setCellValue(pago.getVoucherGcsPath() != null ? "SI" : "NO");
                    row.createCell(12).setCellValue(ai != null ? nvl(ai.get("monto")) : "");
                    row.createCell(13).setCellValue(ai != null ? nvl(ai.get("fechaEmision")) : "");
                    row.createCell(14).setCellValue(ai != null ? nvl(ai.get("banco")) : "");
                    row.createCell(15).setCellValue(ai != null ? nvl(ai.get("numeroDocumento")) : "");

                    // Resaltar filas de pagos PAGADOS
                    if (pago.getEstado() == EstadoPago.PAGADO) {
                        CellStyle pagadoStyle = buildPagadoStyle(wb);
                        for (int c = 0; c < headers.length; c++) {
                            if (row.getCell(c) != null) row.getCell(c).setCellStyle(pagadoStyle);
                        }
                    }
                }
            }

            // Fila de totales
            Row totRow = sheet.createRow(rowIdx + 1);
            totRow.createCell(0).setCellValue("TOTAL PAGADO EN EL PERÍODO");
            BigDecimal totalPagado = facturas.stream()
                    .flatMap(f -> f.getPagos() != null ? f.getPagos().stream() : java.util.stream.Stream.empty())
                    .filter(p -> p.getEstado() == EstadoPago.PAGADO && p.getMonto() != null)
                    .map(PagoFactura::getMonto)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totRow.createCell(7).setCellValue(totalPagado.doubleValue());
            totRow.getCell(0).setCellStyle(subHeaderStyle);
            totRow.getCell(7).setCellStyle(subHeaderStyle);

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fecha efectiva de la factura para clasificarla en un período contable.
     * Cascada: fechaFactura → fechaEmisionFactura → fechaCreacion (subida al sistema).
     */
    private LocalDate fechaEfectivaFactura(Factura f) {
        if (f.getFechaFactura() != null)      return f.getFechaFactura();
        if (f.getFechaEmisionFactura() != null) return f.getFechaEmisionFactura();
        return f.getFechaCreacion();
    }

    private boolean facturaEnPeriodo(Factura f, LocalDate desde, LocalDate hasta) {
        LocalDate fecha = fechaEfectivaFactura(f);
        if (fecha == null) return false;
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    private String gcsPathDeUrl(String url) {
        if (url == null) return null;

        // Formato GCS directo: https://storage.googleapis.com/BUCKET/path/file.pdf
        String gcsPrefix = "https://storage.googleapis.com/" + bucketName + "/";
        if (url.startsWith(gcsPrefix)) return url.substring(gcsPrefix.length());

        // Formato Firebase Storage: https://firebasestorage.googleapis.com/v0/b/BUCKET.appspot.com/o/path%2Ffile.pdf?alt=media&token=...
        if (url.startsWith("https://firebasestorage.googleapis.com/")) {
            try {
                int oIdx = url.indexOf("/o/");
                if (oIdx < 0) return null;
                String encoded = url.substring(oIdx + 3);
                int qIdx = encoded.indexOf('?');
                if (qIdx >= 0) encoded = encoded.substring(0, qIdx);
                return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[PaqueteContabilidad] No se pudo parsear Firebase Storage URL: {}", url);
                return null;
            }
        }

        return null;
    }

    private String sanitize(String nombre) {
        if (nombre == null) return "sin_nombre";
        return nombre.replaceAll("[^a-zA-Z0-9_\\-]", "_").replaceAll("_{2,}", "_");
    }

    private String extensionDe(String gcsPath) {
        if (gcsPath == null) return ".bin";
        String lower = gcsPath.toLowerCase();
        if (lower.endsWith(".pdf"))  return ".pdf";
        if (lower.endsWith(".png"))  return ".png";
        if (lower.endsWith(".jpeg")) return ".jpeg";
        return ".jpg";
    }

    private String nvl(String s) { return s != null ? s : ""; }

    // ── Estilos Excel ─────────────────────────────────────────────────────────

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle buildSubHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle buildTitleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        s.setFont(f);
        return s;
    }

    private CellStyle buildPagadoStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
