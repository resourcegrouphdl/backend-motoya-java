package com.motoyav2;

import com.motoyav2.contrato.domain.enums.TipoDocumentoGenerado;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.model.DatosFinancieros;
import com.motoyav2.contrato.infrastructure.adapter.out.pdf.ThymeleafPdfRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PdfRenderTest {

    static ThymeleafPdfRenderer renderer;
    static Path outDir;

    @BeforeAll
    static void setup() throws IOException {
        renderer = new ThymeleafPdfRenderer();
        outDir = Path.of("target/test-pdfs");
        Files.createDirectories(outDir);
    }

    private Map<String, Object> buildVars() {
        ContratoParaImprimir cp = ContratoParaImprimir.builder()
                .codigo("C-2025-001")
                .nombreTitular("Juan Carlos Pérez López")
                .tipoDeDocumento("DNI")
                .numeroDeDocumento("45678901")
                .domicilioTitular("Av. Los Álamos 123")
                .distritoTitular("San Juan de Miraflores")
                .nombreFiador("María Quispe Ramos")
                .tipoDocumentoFiador("DNI")
                .numeroDocumentoFiador("32109876")
                .domicilioFiador("Jr. Las Flores 456")
                .distritoFiador("Villa María del Triunfo")
                .marcaDeMoto("HONDA")
                .modelo("WAVE 110")
                .anioDelModelo("2025")
                .placaDeRodaje("")
                .colorDeMoto("ROJO")
                .numeroDeSerie("LHJPC110X5E000123")
                .numeroDeMotor("PC110E5E000123")
                .precioTotal(new BigDecimal("5800.00"))
                .precioTotalLetras("CINCO MIL OCHOCIENTOS CON 00/100 SOLES")
                .inicial(new BigDecimal("800.00"))
                .inicialLetras("OCHOCIENTOS CON 00/100 SOLES")
                .numeroDeQuincenas(24)
                .numeroDeMeses(12)
                .montoDeLaQuincena(new BigDecimal("208.33"))
                .montoDeLaQuincenaLetras("DOSCIENTOS OCHO CON 33/100 SOLES")
                .proveedor("DISTRIBUIDORA MOTO PERU SAC")
                .build();

        DatosFinancieros financieros = new DatosFinancieros(
                new BigDecimal("5800.00"),
                new BigDecimal("800.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("18.00"),
                24,
                new BigDecimal("208.33")
        );

        Map<String, Object> vars = new HashMap<>();
        vars.put("numeroContrato", "2025/001/SJM");
        vars.put("cp", cp);
        vars.put("financieros", financieros);
        vars.put("fechaEmision", "22/02/2025");
        vars.put("dia", 22);
        vars.put("mes", "FEBRERO");
        vars.put("anio", 2025);
        vars.put("cuotas", List.of());
        return vars;
    }

    @Test
    void generarPagare() throws IOException {
        byte[] pdf = renderer.render(TipoDocumentoGenerado.PAGARE, buildVars());
        Path out = outDir.resolve("test-pagare.pdf");
        Files.write(out, pdf);
        System.out.println("PDF generado: " + out.toAbsolutePath());
        assert pdf.length > 0 : "PDF vacío";
    }

    @Test
    void generarCartaInstruccion() throws IOException {
        byte[] pdf = renderer.render(TipoDocumentoGenerado.CARTA_INSTRUCCION, buildVars());
        Path out = outDir.resolve("test-carta-instruccion.pdf");
        Files.write(out, pdf);
        System.out.println("PDF generado: " + out.toAbsolutePath());
        assert pdf.length > 0 : "PDF vacío";
    }

    @Test
    void generarCronograma() throws IOException {
        byte[] pdf = renderer.render(TipoDocumentoGenerado.CRONOGRAMA, buildVars());
        Path out = outDir.resolve("test-cronograma.pdf");
        Files.write(out, pdf);
        System.out.println("PDF generado: " + out.toAbsolutePath());
        assert pdf.length > 0 : "PDF vacío";
    }
}