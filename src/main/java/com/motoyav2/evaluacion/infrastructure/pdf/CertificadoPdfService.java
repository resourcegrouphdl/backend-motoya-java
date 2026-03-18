package com.motoyav2.evaluacion.infrastructure.pdf;

import com.motoyav2.evaluacion.domain.model.Expediente;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificadoPdfService {

    private final SpringTemplateEngine templateEngine;

    public byte[] generar(Expediente expediente) {
        try {
            Context ctx = new Context();
            ctx.setVariable("solicitud",  expediente.getSolicitud());
            ctx.setVariable("titular",    expediente.getTitular());
            ctx.setVariable("vehiculo",   expediente.getVehiculo());
            ctx.setVariable("asesor",     expediente.getAsesorAsignado());

            String html = templateEngine.process("evaluacion/certificado-aprobacion", ctx);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generando certificado PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando certificado PDF", e);
        }
    }
}
