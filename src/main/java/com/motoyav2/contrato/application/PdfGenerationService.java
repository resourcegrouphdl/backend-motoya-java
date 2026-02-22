package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.TipoDocumentoGenerado;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import com.motoyav2.contrato.domain.model.DocumentoGenerado;
import com.motoyav2.contrato.domain.port.out.StoragePort;
import com.motoyav2.contrato.infrastructure.adapter.out.pdf.ThymeleafPdfRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfGenerationService {

    private final ThymeleafPdfRenderer pdfRenderer;
    private final StoragePort storagePort;

    public Mono<List<DocumentoGenerado>> generarTodos(Contrato contrato, List<CuotaCronograma> cuotas) {
        return Flux.fromArray(TipoDocumentoGenerado.values())
                .flatMap(tipo -> generarUno(contrato, cuotas, tipo))
                .collectList();
    }

    private Mono<DocumentoGenerado> generarUno(Contrato contrato, List<CuotaCronograma> cuotas, TipoDocumentoGenerado tipo) {
        return Mono.fromCallable(() -> {
            Map<String, Object> variables = buildVariables(contrato, cuotas);
            return pdfRenderer.render(tipo, variables);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(pdfBytes -> {
            String fileName = buildFileName(contrato.numeroContrato(), tipo);
            String path = "contratos/contratos-pdf/" + contrato.id() + "/" + fileName;
            return storagePort.uploadPdf(path, pdfBytes, "application/pdf")
                    .map(url -> DocumentoGenerado.builder()
                            .id(UUID.randomUUID().toString())
                            .tipo(tipo)
                            .urlDocumento(url)
                            .nombreArchivo(fileName)
                            .fechaGeneracion(Instant.now())
                            .generadoPor("SYSTEM")
                            .versionDocumento(1)
                            .build());
        });
    }

    private Map<String, Object> buildVariables(Contrato contrato, List<CuotaCronograma> cuotas) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("numeroContrato", contrato.numeroContrato());
        vars.put("titular", contrato.titular().nombreCompleto());
        vars.put("fiador", contrato.fiador().nombreCompleto());
        vars.put("financieros", contrato.datosFinancieros());
        vars.put("tienda", contrato.tienda().nombreTienda());
        vars.put("cp", contrato.contratoParaImprimir());

        LocalDate hoy = LocalDate.now();
        vars.put("dia", hoy.getDayOfMonth());
        vars.put("mes", formatMes(hoy.getMonth()));
        vars.put("anio", hoy.getYear());
        vars.put("fechaEmision", hoy.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        List<Map<String, Object>> cuotasData = new ArrayList<>();
        ZoneId lima = ZoneId.of("America/Lima");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (cuotas != null) {
            for (CuotaCronograma c : cuotas) {
                Map<String, Object> m = new HashMap<>();
                m.put("numero", c.numeroCuota());
                m.put("fecha", c.fechaVencimiento() != null
                        ? LocalDate.ofInstant(c.fechaVencimiento(), lima).format(fmt) : "");
                m.put("montoCuota", s2(c.montoCuota()));
                m.put("montoCapital", s2(c.montoCapital()));
                m.put("montoInteres", s2(c.montoInteres()));
                m.put("saldo", s2(c.saldoPendiente()));
                cuotasData.add(m);
            }
        }
        vars.put("cuotas", cuotasData);

        return vars;
    }

    private String formatMes(Month month) {
        return switch (month) {
            case JANUARY -> "ENERO";
            case FEBRUARY -> "FEBRERO";
            case MARCH -> "MARZO";
            case APRIL -> "ABRIL";
            case MAY -> "MAYO";
            case JUNE -> "JUNIO";
            case JULY -> "JULIO";
            case AUGUST -> "AGOSTO";
            case SEPTEMBER -> "SEPTIEMBRE";
            case OCTOBER -> "OCTUBRE";
            case NOVEMBER -> "NOVIEMBRE";
            case DECEMBER -> "DICIEMBRE";
        };
    }

    private static BigDecimal s2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildFileName(String numeroContrato, TipoDocumentoGenerado tipo) {
        String slug = numeroContrato != null ? numeroContrato.replace("/", "-") : "sin-numero";
        return switch (tipo) {
            case CONTRATO -> slug + "-contrato.pdf";
            case CRONOGRAMA -> slug + "-cronograma.pdf";
            case PAGARE -> slug + "-pagare.pdf";
            case CARTA_INSTRUCCION -> slug + "-carta-instruccion.pdf";
        };
    }
}
