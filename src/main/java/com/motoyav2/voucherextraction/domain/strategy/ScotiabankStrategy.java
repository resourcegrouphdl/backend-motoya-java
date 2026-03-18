package com.motoyav2.voucherextraction.domain.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción para vouchers de Scotiabank Perú.
 * Formato típico: "Monto: S/ X,XXX.XX" con fecha y hora en un solo campo.
 */
@Component
@Order(4)
public class ScotiabankStrategy implements BancoStrategy {

    private static final Pattern DETECCION = Pattern.compile(
            "(?i)(\\bscotiabank\\b|\\bscotia\\b)", Pattern.UNICODE_CASE);

    private static final Pattern P_MONTO = Pattern.compile(
            "(?i)(?:monto|importe)\\s*[:\\-]?\\s*(S/\\.?\\s*[\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_DESTINATARIO = Pattern.compile(
            "(?i)(?:destinatario|beneficiario)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    // "Fecha y hora: 17/03/2026 15:35"
    private static final Pattern P_FECHA_HORA = Pattern.compile(
            "(?i)fecha\\s+(?:y\\s+)?hora\\s*[:\\-]?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}\\s+\\d{1,2}:\\d{2})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_FECHA = Pattern.compile(
            "(?i)fecha\\s*[:\\-]?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_NRO = Pattern.compile(
            "(?i)n[°º\\.]*\\s*(?:de\\s+)?operaci[oó]n\\s*[:\\-]?\\s*([\\d]{6,20})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CONCEPTO = Pattern.compile(
            "(?i)(?:concepto|descripci[oó]n)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CANAL = Pattern.compile(
            "(?i)canal\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)", Pattern.UNICODE_CASE);

    @Override
    public String getBancoNombre() { return "SCOTIABANK"; }

    @Override
    public boolean soporta(String fullText) {
        return fullText != null && DETECCION.matcher(fullText).find();
    }

    @Override
    public Map<String, String> extraer(String fullText) {
        Map<String, String> campos = new HashMap<>();
        if (fullText == null || fullText.isBlank()) return campos;

        match(P_MONTO, fullText)
                .ifPresent(v -> campos.put("montoPagado", v.startsWith("S/") ? v : "S/ " + v));

        match(P_FECHA_HORA, fullText)
                .or(() -> match(P_FECHA, fullText))
                .ifPresent(v -> campos.put("fechaPago", v));

        match(P_DESTINATARIO, fullText).ifPresent(v -> campos.put("pagadoA", v));
        match(P_NRO, fullText).ifPresent(v -> campos.put("numeroOperacion", v));
        match(P_CONCEPTO, fullText).ifPresent(v -> campos.put("concepto", v));
        match(P_CANAL, fullText).ifPresent(v -> campos.put("canal", v));
        campos.put("banco", "SCOTIABANK");
        return campos;
    }

    private static Optional<String> match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
