package com.motoyav2.voucherextraction.domain.strategy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracción para vouchers de Interbank.
 * Formato típico: "Label: Valor" en la misma línea.
 */
@Component
@Order(2)
public class InterbankStrategy implements BancoStrategy {

    private static final Pattern DETECCION = Pattern.compile(
            "(?i)(\\binterbank\\b|BANCO\\s+INTERNACIONAL\\s+DEL\\s+PER[ÚU])",
            Pattern.UNICODE_CASE);

    private static final Pattern P_IMPORTE = Pattern.compile(
            "(?i)(?:importe|monto)\\s*[:\\-]?\\s*(S/\\.?\\s*[\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_IMPORTE_FB = Pattern.compile(
            "\\bS/\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    private static final Pattern P_FECHA = Pattern.compile(
            "(?i)fecha\\s*[:\\-]?\\s*(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_HORA = Pattern.compile(
            "(?i)hora\\s*[:\\-]?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_BENEFICIARIO = Pattern.compile(
            "(?i)(?:beneficiario|destinatario|pagado\\s+a)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CUENTA_ORIGEN = Pattern.compile(
            "(?i)cuenta\\s+(?:de\\s+)?(?:cargo|origen|d[eé]bito)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_NRO_OPERACION = Pattern.compile(
            "(?i)n[úu]mero\\s+(?:de\\s+)?(?:operaci[oó]n|transacci[oó]n)\\s*[:\\-]?\\s*([\\d]{6,20})",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CONCEPTO = Pattern.compile(
            "(?i)(?:concepto|motivo|descripci[oó]n)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    private static final Pattern P_CANAL = Pattern.compile(
            "(?i)(?:canal|medio)\\s*[:\\-]?\\s*(.+?)(?:\\r?\\n|$)",
            Pattern.UNICODE_CASE);

    @Override
    public String getBancoNombre() { return "INTERBANK"; }

    @Override
    public boolean soporta(String fullText) {
        return fullText != null && DETECCION.matcher(fullText).find();
    }

    @Override
    public Map<String, String> extraer(String fullText) {
        Map<String, String> campos = new HashMap<>();
        if (fullText == null || fullText.isBlank()) return campos;

        match(P_IMPORTE, fullText)
                .or(() -> match(P_IMPORTE_FB, fullText))
                .ifPresent(v -> campos.put("montoPagado", v.startsWith("S/") ? v : "S/ " + v));

        // Concatenar fecha y hora si vienen en campos separados
        String fecha = match(P_FECHA, fullText).orElse(null);
        String hora  = match(P_HORA, fullText).orElse(null);
        if (fecha != null) {
            campos.put("fechaPago", hora != null ? fecha + " " + hora : fecha);
        }

        match(P_BENEFICIARIO, fullText).ifPresent(v -> campos.put("pagadoA", v));
        match(P_CUENTA_ORIGEN, fullText).ifPresent(v -> campos.put("desde", v));
        match(P_NRO_OPERACION, fullText).ifPresent(v -> campos.put("numeroOperacion", v));
        match(P_CONCEPTO, fullText).ifPresent(v -> campos.put("concepto", v));
        match(P_CANAL, fullText).ifPresent(v -> campos.put("canal", v));
        campos.put("banco", "INTERBANK");
        return campos;
    }

    private static Optional<String> match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.of(m.group(1).trim()) : Optional.empty();
    }
}
