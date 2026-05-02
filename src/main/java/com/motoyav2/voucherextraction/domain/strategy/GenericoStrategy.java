package com.motoyav2.voucherextraction.domain.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estrategia genérica de fallback para cualquier banco peruano no reconocido.
 * Siempre retorna true en soporta() — debe ser la última en la cadena (@Order MAX).
 * Aplica patrones generales que funcionan en múltiples formatos bancarios.
 */
@Component
@Order(Integer.MAX_VALUE)
public class GenericoStrategy implements BancoStrategy {

    // "S/ X,XXX.XX" — formato estándar peruano
    private static final Pattern P_MONTO_SOL = Pattern.compile(
            "(?i)\\bS/\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    // "Monto: X,XXX.XX" o "Total: 289.40" — etiqueta explícita sin símbolo
    private static final Pattern P_MONTO_LABEL = Pattern.compile(
            "(?i)(?:monto|importe|total|amount)\\s*[:\\-]?\\s*(?:s/\\.?)?\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    // Fecha en texto: "Martes, 17 marzo 2026"
    private static final Pattern P_FECHA_TEXTO = Pattern.compile(
            "(?i)((?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)"
                    + "\\s*,?\\s*\\d{1,2}\\s+(?:de\\s+)?[a-záéíóúñ]+\\s+\\d{4}"
                    + "(?:\\s*[-–]\\s*\\d{1,2}:\\d{2}(?:\\s*[ap]\\.?\\s*m\\.?)?)?)",
            Pattern.UNICODE_CASE);

    // Fecha numérica con hora opcional: "17/03/2026 15:35"
    private static final Pattern P_FECHA_NUM = Pattern.compile(
            "(?i)(?:fecha|date)\\s*[:\\-]?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}"
                    + "(?:\\s+\\d{1,2}:\\d{2})?)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_NRO_OPERACION = Pattern.compile(
            "(?i)n[°ºúu\\.]*\\s*(?:de\\s+)?(?:operaci[oó]n|transacci[oó]n)"
                    + "\\s*[:\\-]?\\r?\\n?\\s*([\\d]{6,20})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_BENEFICIARIO = Pattern.compile(
            "(?i)(?:pagado\\s+a|beneficiario|destinatario)\\s*[:\\-]?\\r?\\n?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CANAL = Pattern.compile(
            "(?i)canal\\s*[:\\-]?\\r?\\n?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CONCEPTO = Pattern.compile(
            "(?i)(?:concepto|servicio|descripci[oó]n|motivo)\\s*[:\\-]?\\r?\\n?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    @Override
    public String getBancoNombre() { return "GENERICO"; }

    @Override
    public boolean soporta(String fullText) { return true; }

    @Override
    public Map<String, String> extraer(String fullText) {
        Map<String, String> campos = new HashMap<>();
        if (fullText == null || fullText.isBlank()) return campos;

        match(P_MONTO_SOL, fullText)
                .or(() -> match(P_MONTO_LABEL, fullText))
                .ifPresent(v -> campos.put("montoPagado", "S/ " + v));

        match(P_FECHA_TEXTO, fullText)
                .or(() -> match(P_FECHA_NUM, fullText))
                .ifPresent(v -> campos.put("fechaPago", v));

        match(P_NRO_OPERACION, fullText).ifPresent(v -> campos.put("numeroOperacion", v));
        match(P_BENEFICIARIO, fullText).ifPresent(v -> campos.put("pagadoA", v));
        match(P_CANAL, fullText).ifPresent(v -> campos.put("canal", v));
        match(P_CONCEPTO, fullText).ifPresent(v -> campos.put("servicio", v));
        campos.put("banco", "DESCONOCIDO");
        return campos;
    }

    private static Optional<String> match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
