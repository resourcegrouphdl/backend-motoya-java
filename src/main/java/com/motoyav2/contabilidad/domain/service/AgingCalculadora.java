package com.motoyav2.contabilidad.domain.service;

import com.google.cloud.Timestamp;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Lógica pura de aging/mora — sin dependencias de I/O.
 */
@Component
public class AgingCalculadora {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /**
     * Calcula los días de mora a partir del campo {@code fechaVencimientoPrimerCuotaImpaga},
     * que puede llegar como String "YYYY-MM-DD" o como {@link com.google.cloud.Timestamp}.
     *
     * @return días de mora (0 si no hay mora o el campo es nulo)
     */
    public int calcularDiasMora(Object raw, LocalDate hoy) {
        if (raw == null) return 0;

        LocalDate fechaVencimiento;
        if (raw instanceof String s) {
            try {
                fechaVencimiento = LocalDate.parse(s.substring(0, 10));
            } catch (Exception e) {
                return 0;
            }
        } else if (raw instanceof Timestamp ts) {
            fechaVencimiento = ts.toDate().toInstant().atZone(LIMA).toLocalDate();
        } else {
            // Intento genérico via toString
            try {
                fechaVencimiento = LocalDate.parse(raw.toString().substring(0, 10));
            } catch (Exception e) {
                return 0;
            }
        }

        long dias = ChronoUnit.DAYS.between(fechaVencimiento, hoy);
        return (int) Math.max(0, dias);
    }

    /**
     * Clasifica los días de mora en un tramo de aging.
     */
    public String clasificarTramo(int diasMora) {
        if (diasMora == 0) return "AL_DIA";
        if (diasMora <= 30) return "1_30";
        if (diasMora <= 60) return "31_60";
        if (diasMora <= 90) return "61_90";
        return "MAS_90";
    }

    /**
     * Retorna una etiqueta legible para el tramo.
     */
    public String labelTramo(String tramo) {
        return switch (tramo) {
            case "AL_DIA" -> "Al día";
            case "1_30"   -> "1-30 días";
            case "31_60"  -> "31-60 días";
            case "61_90"  -> "61-90 días";
            case "MAS_90" -> "Más de 90 días";
            default       -> tramo;
        };
    }
}
