package com.motoyav2.voucherextraction.domain.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción para vouchers de BBVA Perú.
 * El monto puede aparecer prominentemente al inicio del voucher sin etiqueta.
 */
@Component
@Order(3)
public class BbvaStrategy implements BancoStrategy {

    private static final Pattern DETECCION = Pattern.compile(
            "(?i)(\\bBBVA\\b|BANCO\\s+CONTINENTAL)", Pattern.UNICODE_CASE);

    private static final Pattern P_MONTO_ETIQUETA = Pattern.compile(
            "(?i)(?:importe|monto)\\s*[:\\-]?\\s*(S/\\.?\\s*[\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.UNICODE_CASE);

    // BBVA a veces muestra el monto solo en una línea al inicio
    private static final Pattern P_MONTO_LINEA = Pattern.compile(
            "^\\s*(S/\\.?\\s*[\\d,]+(?:\\.\\d{1,2})?)\\s*$", Pattern.MULTILINE);

    private static final Pattern P_DESTINATARIO = Pattern.compile(
            "(?i)(?:(?<=\\bA\\s{0,3}:\\s{0,3})|(?:destinatario|beneficiario)\\s*[:\\-]?\\s*)(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_FECHA = Pattern.compile(
            "(?i)fecha\\s*[:\\-]?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_HORA = Pattern.compile(
            "(?i)hora\\s*[:\\-]?\\s*(\\d{1,2}:\\d{2})", Pattern.UNICODE_CASE);

    private static final Pattern P_NRO = Pattern.compile(
            "(?i)n[úu]mero\\s+(?:de\\s+)?(?:transacci[oó]n|operaci[oó]n)\\s*[:\\-]?\\s*([\\d]{6,20})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CONCEPTO = Pattern.compile(
            "(?i)(?:concepto|descripci[oó]n)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CANAL = Pattern.compile(
            "(?i)canal\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)", Pattern.UNICODE_CASE);

    @Override
    public String getBancoNombre() { return "BBVA"; }

    @Override
    public boolean soporta(String fullText) {
        return fullText != null && DETECCION.matcher(fullText).find();
    }

    @Override
    public Map<String, String> extraer(String fullText) {
        Map<String, String> campos = new HashMap<>();
        if (fullText == null || fullText.isBlank()) return campos;

        match(P_MONTO_ETIQUETA, fullText)
                .or(() -> match(P_MONTO_LINEA, fullText))
                .ifPresent(v -> campos.put("montoPagado", v.startsWith("S/") ? v : "S/ " + v));

        String fecha = match(P_FECHA, fullText).orElse(null);
        String hora  = match(P_HORA, fullText).orElse(null);
        if (fecha != null) {
            campos.put("fechaPago", hora != null ? fecha + " " + hora : fecha);
        }

        match(P_DESTINATARIO, fullText).ifPresent(v -> campos.put("pagadoA", v));
        match(P_NRO, fullText).ifPresent(v -> campos.put("numeroOperacion", v));
        match(P_CONCEPTO, fullText).ifPresent(v -> campos.put("concepto", v));
        match(P_CANAL, fullText).ifPresent(v -> campos.put("canal", v));
        campos.put("banco", "BBVA");
        return campos;
    }

    private static Optional<String> match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
