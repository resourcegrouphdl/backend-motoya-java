package com.motoyav2.voucherextraction.domain.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción para vouchers del Banco de Crédito del Perú (BCP).
 *
 * El formato de la app BCP coloca la etiqueta en una línea y el valor en la siguiente:
 *   Monto pagado
 *   S/ 1,200.00
 *   Pagado a
 *   CORPORACION GIANSA MOTOS SAC
 */
@Component
@Order(1)
public class BcpStrategy implements BancoStrategy {

    private static final Pattern DETECCION = Pattern.compile(
            "(?i)(banco\\s+de\\s+cr[eé]dito|\\bBCP\\b|banca\\s+por\\s+internet\\s+bcp|yape\\s+bcp)",
            Pattern.UNICODE_CASE);

    // Monto: "Monto pagado\nS/ 1,200.00"
    private static final Pattern P_MONTO = Pattern.compile(
            "(?i)monto\\s+pagado\\s*\\r?\\n\\s*(S/\\.?\\s*[\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.UNICODE_CASE);

    // Fallback: cualquier "S/ X,XXX.XX" en el texto
    private static final Pattern P_MONTO_FB = Pattern.compile(
            "\\bS/\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    // "Martes, 17 marzo 2026 - 5:35 p.m"
    private static final Pattern P_FECHA = Pattern.compile(
            "(?i)((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)"
                    + "\\s*,?\\s*\\d{1,2}\\s+(?:de\\s+)?[a-záéíóúñ]+\\s+\\d{4}"
                    + "(?:\\s*[-–]\\s*\\d{1,2}:\\d{2}\\s*(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)?)?)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_PAGADO_A = Pattern.compile(
            "(?i)pagado\\s+a\\s*\\r?\\n\\s*(.+?)(?:\\r?\\n|$)", Pattern.UNICODE_CASE);

    private static final Pattern P_SERVICIO = Pattern.compile(
            "(?i)\\bservicio\\b\\s*\\r?\\n\\s*(.+?)(?:\\r?\\n|$)", Pattern.UNICODE_CASE);

    private static final Pattern P_CODIGO_USUARIO = Pattern.compile(
            "(?i)c[oó]digo\\s+(?:de\\s+)?usuario\\s*\\r?\\n?\\s*(\\d{6,15})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_DESDE = Pattern.compile(
            "(?i)\\bdesde\\b\\s*\\r?\\n\\s*(.+?)(?:\\r?\\n|$)", Pattern.UNICODE_CASE);

    private static final Pattern P_CANAL = Pattern.compile(
            "(?i)\\bcanal\\b\\s*\\r?\\n\\s*(.+?)(?:\\r?\\n|$)", Pattern.UNICODE_CASE);

    private static final Pattern P_NRO_OPERACION = Pattern.compile(
            "(?i)n[°º\\.]*\\s*(?:de\\s+)?operaci[oó]n\\s*\\r?\\n?\\s*([\\d]{6,20})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_NRO_OPERACION_INLINE = Pattern.compile(
            "(?i)n[úu]mero\\s+(?:de\\s+)?operaci[oó]n\\s*[:\\-]?\\s*([\\d]{6,20})",
            Pattern.UNICODE_CASE);

    @Override
    public String getBancoNombre() { return "BCP"; }

    @Override
    public boolean soporta(String fullText) {
        return fullText != null && DETECCION.matcher(fullText).find();
    }

    @Override
    public Map<String, String> extraer(String fullText) {
        Map<String, String> campos = new HashMap<>();
        if (fullText == null || fullText.isBlank()) return campos;

        match(P_MONTO, fullText)
                .or(() -> match(P_MONTO_FB, fullText))
                .ifPresent(v -> campos.put("montoPagado", normalizarMonto(v)));

        match(P_FECHA, fullText).ifPresent(v -> campos.put("fechaPago", v));
        match(P_PAGADO_A, fullText).ifPresent(v -> campos.put("pagadoA", v));
        match(P_SERVICIO, fullText).ifPresent(v -> campos.put("servicio", v));
        match(P_CODIGO_USUARIO, fullText).ifPresent(v -> campos.put("codigoUsuario", v));
        match(P_DESDE, fullText).ifPresent(v -> campos.put("desde", v));
        match(P_CANAL, fullText).ifPresent(v -> campos.put("canal", v));
        match(P_NRO_OPERACION, fullText)
                .or(() -> match(P_NRO_OPERACION_INLINE, fullText))
                .ifPresent(v -> campos.put("numeroOperacion", v));

        campos.put("banco", "BCP");
        return campos;
    }

    private static String normalizarMonto(String v) {
        return v.startsWith("S/") ? v : "S/ " + v;
    }

    private static Optional<String> match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
