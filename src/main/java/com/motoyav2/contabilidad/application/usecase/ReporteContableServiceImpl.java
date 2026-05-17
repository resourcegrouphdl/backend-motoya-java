package com.motoyav2.contabilidad.application.usecase;

import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.common.util.concurrent.MoreExecutors;
import com.motoyav2.contabilidad.domain.model.*;
import com.motoyav2.contabilidad.domain.port.in.*;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.ContratoDocument;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.DatosFinancierosEmbedded;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ComisionDocument;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteContableServiceImpl implements
        GenerarLiquidacionComisionesUseCase,
        GenerarPdfLiquidacionUseCase,
        GenerarExcelContratosUseCase {

    private final Firestore db;

    private static final String COL_COMISIONES  = "finanzas_comisiones";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> ESTADOS_CERRADO = List.of("FIRMADO", "ACTIVO", "COMPLETADO");

    // ── Preview JSON ──────────────────────────────────────────────────────────

    @Override
    public Mono<ReporteLiquidacionResponse> generar(LocalDate desde, LocalDate hasta, String tiendaId) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return queryComisiones(d, h, tiendaId)
                .collectList()
                .map(list -> agrupar(list, d, h));
    }

    // ── Query directa a finanzas_comisiones (sin índice compuesto) ────────────
    // Solo usa igualdad en tiendaId; el filtro de fechas se aplica en memoria.
    private Flux<ComisionDocument> queryComisiones(LocalDate desde, LocalDate hasta, String tiendaId) {
        Query q = (tiendaId != null && !tiendaId.isBlank())
                ? db.collection(COL_COMISIONES).whereEqualTo("tiendaId", tiendaId)
                : db.collection(COL_COMISIONES);

        return toFlux(q.get())
                .map(doc -> doc.toObject(ComisionDocument.class))
                .filter(Objects::nonNull)
                .filter(doc -> enPeriodo(doc, desde, hasta));
    }

    private boolean enPeriodo(ComisionDocument doc, LocalDate desde, LocalDate hasta) {
        try {
            LocalDate inicio = doc.getPeriodoInicio() != null ? LocalDate.parse(doc.getPeriodoInicio()) : null;
            LocalDate fin    = doc.getPeriodoFin()    != null ? LocalDate.parse(doc.getPeriodoFin())    : null;
            if (desde != null && fin    != null && fin.isBefore(desde))  return false;
            if (hasta != null && inicio != null && inicio.isAfter(hasta)) return false;
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Override
    public Mono<byte[]> generarPdf(LocalDate desde, LocalDate hasta, String tiendaId) {
        return generar(desde, hasta, tiendaId)
                .flatMap(reporte -> Mono.fromCallable(() -> buildPdf(reporte))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    // ── Excel contratos ───────────────────────────────────────────────────────

    @Override
    public Mono<byte[]> generarExcel(LocalDate desde, LocalDate hasta, String tiendaId) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return queryContratos(d, h, tiendaId)
                .collectList()
                .flatMap(rows -> Mono.fromCallable(() -> buildExcel(rows, d, h))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    // ── Agrupación comisiones ─────────────────────────────────────────────────

    private ReporteLiquidacionResponse agrupar(List<ComisionDocument> list, LocalDate desde, LocalDate hasta) {
        Map<String, String>                                   tiendaNombres     = new LinkedHashMap<>();
        Map<String, Map<String, List<ComisionDocument>>>      porTiendaVendedor = new LinkedHashMap<>();

        for (ComisionDocument c : list) {
            String tid   = c.getTiendaId()    != null ? c.getTiendaId()    : "sin-tienda";
            String tName = c.getTiendaNombre() != null ? c.getTiendaNombre() : "Sin tienda";
            String vid   = c.getVendedorId()  != null ? c.getVendedorId()  : "sin-vendedor";
            tiendaNombres.putIfAbsent(tid, tName);
            porTiendaVendedor.computeIfAbsent(tid, k -> new LinkedHashMap<>())
                    .computeIfAbsent(vid, k -> new ArrayList<>())
                    .add(c);
        }

        List<LiquidacionTiendaDTO> tiendas   = new ArrayList<>();
        BigDecimal                 totalGral  = BigDecimal.ZERO;
        int                        totalItems = 0;

        for (Map.Entry<String, Map<String, List<ComisionDocument>>> te : porTiendaVendedor.entrySet()) {
            List<LiquidacionVendedorDTO> vendedores    = new ArrayList<>();
            BigDecimal                   subtotalTienda = BigDecimal.ZERO;

            for (List<ComisionDocument> comisiones : te.getValue().values()) {
                ComisionDocument primero = comisiones.get(0);

                List<ItemComisionDTO> items = comisiones.stream()
                        .map(c -> new ItemComisionDTO(
                                c.getId(),
                                c.getContratoId(),
                                c.getClienteNombre(),
                                c.getClienteDocumento(),
                                monto(c.getMontoComision()),
                                c.getEstado() != null ? c.getEstado() : "",
                                formatFecha(c.getPeriodoInicio())))
                        .toList();

                BigDecimal totalVendedor = comisiones.stream()
                        .map(c -> monto(c.getMontoComision()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                vendedores.add(new LiquidacionVendedorDTO(
                        primero.getVendedorId(),
                        primero.getVendedorNombre(),
                        primero.getVendedorDocumento(),
                        comisiones.size(),
                        totalVendedor,
                        items));

                subtotalTienda = subtotalTienda.add(totalVendedor);
                totalItems    += comisiones.size();
            }

            tiendas.add(new LiquidacionTiendaDTO(
                    te.getKey(), tiendaNombres.get(te.getKey()), vendedores, subtotalTienda));
            totalGral = totalGral.add(subtotalTienda);
        }

        return new ReporteLiquidacionResponse(desde, hasta, tiendas, totalGral, totalItems);
    }

    private BigDecimal monto(Double v) {
        return v != null ? BigDecimal.valueOf(v) : BigDecimal.ZERO;
    }

    private String formatFecha(String isoDate) {
        if (isoDate == null) return "";
        try { return LocalDate.parse(isoDate).format(DATE_FMT); }
        catch (Exception e) { return isoDate; }
    }

    // ── PDF render ────────────────────────────────────────────────────────────

    private byte[] buildPdf(ReporteLiquidacionResponse reporte) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(buildHtml(reporte), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de liquidación", e);
        }
    }

    private String buildHtml(ReporteLiquidacionResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>");
        sb.append("body{font-family:Arial,sans-serif;font-size:9px;margin:15px;color:#222;}");
        sb.append("h1{font-size:13px;color:#1a237e;margin-bottom:2px;}");
        sb.append(".sub{font-size:8px;color:#666;margin-bottom:10px;}");
        sb.append(".tienda{background:#1a237e;color:#fff;padding:5px 8px;font-size:11px;font-weight:bold;margin-top:14px;}");
        sb.append(".vendedor{background:#e8eaf6;color:#1a237e;padding:4px 8px;font-size:10px;font-weight:bold;margin-top:5px;}");
        sb.append("table{width:100%;border-collapse:collapse;margin-top:2px;}");
        sb.append("th{background:#3949ab;color:#fff;padding:4px 5px;text-align:left;font-size:8px;}");
        sb.append("td{padding:3px 5px;border-bottom:1px solid #e0e0e0;font-size:8px;}");
        sb.append("tr:nth-child(even) td{background:#fafafa;}");
        sb.append(".sv td{background:#e8eaf6!important;font-weight:bold;}");
        sb.append(".st td{background:#c5cae9!important;font-weight:bold;font-size:9px;}");
        sb.append(".tg td{background:#1a237e!important;color:#fff!important;font-weight:bold;font-size:10px;}");
        sb.append(".pend{color:#e65100;}.pago{color:#2e7d32;}.proc{color:#1565c0;}");
        sb.append("</style></head><body>");

        sb.append("<h1>LIQUIDACIÓN DE COMISIONES POR VENDEDOR</h1>");
        sb.append(String.format("<div class=\"sub\">Período: %s &mdash; %s &nbsp;|&nbsp; Generado: %s</div>",
                r.desde().format(DATE_FMT), r.hasta().format(DATE_FMT), LocalDate.now().format(DATE_FMT)));

        for (LiquidacionTiendaDTO tienda : r.tiendas()) {
            sb.append("<div class=\"tienda\">TIENDA: ").append(esc(tienda.tiendaNombre())).append("</div>");

            for (LiquidacionVendedorDTO v : tienda.vendedores()) {
                sb.append("<div class=\"vendedor\">Vendedor: ").append(esc(v.vendedorNombre()));
                if (v.vendedorDocumento() != null && !v.vendedorDocumento().isEmpty())
                    sb.append(" &nbsp;|&nbsp; ").append(esc(v.vendedorDocumento()));
                sb.append(" &nbsp;|&nbsp; ").append(v.totalVentas()).append(" ventas</div>");

                sb.append("<table><thead><tr>")
                  .append("<th>N°</th><th>Contrato</th><th>Cliente</th>")
                  .append("<th>Documento</th><th>Período</th><th>Comisión</th><th>Estado</th>")
                  .append("</tr></thead><tbody>");

                int n = 1;
                for (ItemComisionDTO item : v.items()) {
                    String estadoClass = switch (item.estado()) {
                        case "PAGADO"     -> "pago";
                        case "EN_PROCESO" -> "proc";
                        default           -> "pend";
                    };
                    sb.append("<tr>")
                      .append("<td>").append(n++).append("</td>")
                      .append("<td>").append(esc(item.contratoId())).append("</td>")
                      .append("<td>").append(esc(item.clienteNombre())).append("</td>")
                      .append("<td>").append(esc(item.clienteDocumento())).append("</td>")
                      .append("<td>").append(esc(item.fecha())).append("</td>")
                      .append("<td><strong>S/ ").append(fmt(item.montoComision())).append("</strong></td>")
                      .append("<td class=\"").append(estadoClass).append("\">").append(esc(item.estado())).append("</td>")
                      .append("</tr>");
                }

                sb.append("<tr class=\"sv\"><td colspan=\"5\"><strong>SUBTOTAL ")
                  .append(esc(v.vendedorNombre().toUpperCase())).append("</strong></td>")
                  .append("<td><strong>S/ ").append(fmt(v.totalComision())).append("</strong></td>")
                  .append("<td></td></tr>");
                sb.append("</tbody></table>");
            }

            sb.append("<table><tbody><tr class=\"st\">")
              .append("<td colspan=\"5\"><strong>TOTAL TIENDA: ").append(esc(tienda.tiendaNombre().toUpperCase())).append("</strong></td>")
              .append("<td><strong>S/ ").append(fmt(tienda.subtotal())).append("</strong></td>")
              .append("<td></td></tr></tbody></table>");
        }

        sb.append("<table style=\"margin-top:12px;\"><tbody><tr class=\"tg\">")
          .append("<td colspan=\"5\"><strong>TOTAL GENERAL</strong></td>")
          .append("<td><strong>S/ ").append(fmt(r.totalGeneral())).append("</strong></td>")
          .append("<td></td></tr></tbody></table>");

        sb.append("</body></html>");
        return sb.toString();
    }

    // ── Contratos Firestore query ─────────────────────────────────────────────

    private Flux<ContratoReporteRow> queryContratos(LocalDate desde, LocalDate hasta, String tiendaId) {
        Timestamp fromTs = Timestamp.of(Date.from(desde.atStartOfDay(ZoneOffset.UTC).toInstant()));
        Timestamp toTs   = Timestamp.of(Date.from(hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));

        Query q = db.collection("contratos")
                .whereGreaterThanOrEqualTo("fechaActualizacion", fromTs)
                .whereLessThanOrEqualTo("fechaActualizacion", toTs);

        return toFlux(q.get())
                .map(doc -> doc.toObject(ContratoDocument.class))
                .filter(Objects::nonNull)
                .filter(c -> ESTADOS_CERRADO.contains(c.getEstado()))
                .filter(c -> tiendaId == null
                        || (c.getTienda() != null && tiendaId.equals(c.getTienda().getTiendaId())))
                .map(this::toContratoRow);
    }

    private ContratoReporteRow toContratoRow(ContratoDocument doc) {
        String nombre  = "";
        String tipDoc  = "";
        String numDoc  = "";
        String tel     = "";
        if (doc.getTitular() != null) {
            nombre = trim(doc.getTitular().getNombres()) + " " + trim(doc.getTitular().getApellidos());
            tipDoc = trim(doc.getTitular().getTipoDocumento());
            numDoc = trim(doc.getTitular().getNumeroDocumento());
            tel    = trim(doc.getTitular().getTelefono());
        }

        String tienda  = doc.getTienda() != null ? trim(doc.getTienda().getNombreTienda()) : "";

        Double precio  = null, cuotaIni = null, monto = null, cuota = null, tasa = null;
        Integer cuotas = null;
        String marcaMod = "";
        if (doc.getDatosFinancieros() != null) {
            DatosFinancierosEmbedded df = doc.getDatosFinancieros();
            precio   = df.getPrecioVehiculo();
            cuotaIni = df.getCuotaInicial();
            monto    = df.getMontoFinanciado();
            cuotas   = df.getNumeroCuotas();
            cuota    = df.getCuotaMensual();
            tasa     = df.getTasaInteresAnual();
            marcaMod = Stream.of(df.getMarcaVehiculo(), df.getModeloVehiculo(), df.getAnioVehiculo())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
        }

        String fechaCierre = "";
        if (doc.getFechaActualizacion() != null) {
            fechaCierre = doc.getFechaActualizacion().toDate().toInstant()
                    .atZone(ZoneOffset.UTC).toLocalDate().format(DATE_FMT);
        }

        return new ContratoReporteRow(
                trim(doc.getNumeroContrato()), trim(doc.getEstado()),
                tienda, nombre.trim(), tipDoc, numDoc, tel,
                marcaMod, precio, cuotaIni, monto, cuotas, cuota, tasa, fechaCierre);
    }

    // ── Excel contratos ───────────────────────────────────────────────────────

    private byte[] buildExcel(List<ContratoReporteRow> rows, LocalDate desde, LocalDate hasta) {
        String[] headers = {
            "N° Contrato", "Estado", "Tienda",
            "Cliente", "Tipo Doc.", "N° Documento", "Teléfono",
            "Vehículo", "Precio Vehículo", "Cuota Inicial", "Monto Financiado",
            "N° Cuotas", "Cuota Mensual", "TEA %", "Fecha Cierre"
        };

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Contratos");

            // Fila título
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("CONTRATOS CERRADOS — Período " +
                    desde.format(DATE_FMT) + " al " + hasta.format(DATE_FMT));
            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);

            // Headers
            CellStyle hs = headerStyle(wb);
            Row hRow = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hs);
            }

            // Data rows
            CellStyle numStyle = numericStyle(wb);
            int ri = 2;
            for (ContratoReporteRow row : rows) {
                Row r = sheet.createRow(ri++);
                cell(r, 0,  row.numeroContrato());
                cell(r, 1,  row.estado());
                cell(r, 2,  row.tiendaNombre());
                cell(r, 3,  row.clienteNombre());
                cell(r, 4,  row.clienteTipoDocumento());
                cell(r, 5,  row.clienteNumeroDocumento());
                cell(r, 6,  row.clienteTelefono());
                cell(r, 7,  row.marcaModelo());
                cellNum(r, 8,  row.precioVehiculo(),   numStyle);
                cellNum(r, 9,  row.cuotaInicial(),     numStyle);
                cellNum(r, 10, row.montoFinanciado(),  numStyle);
                if (row.numeroCuotas() != null) r.createCell(11).setCellValue(row.numeroCuotas());
                cellNum(r, 12, row.cuotaMensual(),     numStyle);
                cellNum(r, 13, row.tasaInteresAnual(), numStyle);
                cell(r, 14, row.fechaCierre());
            }

            // Fila total
            Row totalRow = sheet.createRow(ri);
            CellStyle ts = totalStyle(wb);
            Cell tcell = totalRow.createCell(0);
            tcell.setCellValue("Total contratos: " + rows.size());
            tcell.setCellStyle(ts);

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel contratos", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fmt(BigDecimal v) {
        return v != null ? String.format("%,.2f", v) : "0.00";
    }

    private String esc(String s) {
        return s != null ? s : "";
    }

    private String trim(String s) {
        return s != null ? s.trim() : "";
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle numericStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        return s;
    }

    private CellStyle totalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private void cell(Row row, int col, String val) {
        row.createCell(col).setCellValue(val != null ? val : "");
    }

    private void cellNum(Row row, int col, Double val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val != null) c.setCellValue(val);
        c.setCellStyle(style);
    }

    // ── Firestore reactive utils (inline para evitar dependencia cruzada) ──────

    private static Mono<QuerySnapshot> toMono(com.google.api.core.ApiFuture<QuerySnapshot> future) {
        return Mono.fromFuture(() -> {
            CompletableFuture<QuerySnapshot> cf = new CompletableFuture<>();
            ApiFutures.addCallback(future, new ApiFutureCallback<>() {
                @Override public void onSuccess(QuerySnapshot r) { cf.complete(r); }
                @Override public void onFailure(Throwable t)     { cf.completeExceptionally(t); }
            }, MoreExecutors.directExecutor());
            return cf;
        });
    }

    private static Flux<DocumentSnapshot> toFlux(com.google.api.core.ApiFuture<QuerySnapshot> future) {
        return toMono(future).flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()));
    }
}
