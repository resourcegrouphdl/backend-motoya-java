package com.motoyav2.cobranza.domain;

import com.motoyav2.cobranza.domain.enums.NivelEstrategia;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Fuente única de verdad para cálculo de mora y nivel de estrategia.
 *
 * Reemplaza tres implementaciones divergentes que coexistían en
 * MoraDiariaService, RecordatoriosPagoService y EstrategiaAutomaticaService.
 *
 * Tramos regulación interna Motoya:
 *   1–15 días  → MORA_TEMPRANA
 *   16–30 días → MORA_MEDIA
 *   31–60 días → MORA_CRITICA
 *   61+  días  → JUDICIAL
 */
public final class NivelMoraCalculadora {

    public static final ZoneId  LIMA              = ZoneId.of("America/Lima");
    public static final double  MORA_DIARIA_SOLES = 3.0;

    private NivelMoraCalculadora() {}

    // ── Mora ─────────────────────────────────────────────────────────────────

    /**
     * Días transcurridos desde la cuota más antigua no pagada cuya fecha ya venció.
     * Devuelve 0 si no hay mora.
     */
    public static int diasMora(CasoCobranzaDocument caso, LocalDate hoy) {
        if (caso.getCronograma() == null) return 0;
        return caso.getCronograma().stream()
                .filter(c -> !"PAGADA".equalsIgnoreCase(c.getEstado())
                        && c.getFechaVencimiento() != null)
                .mapToInt(c -> {
                    try {
                        LocalDate venc = LocalDate.parse(c.getFechaVencimiento());
                        return (int) Math.max(0, ChronoUnit.DAYS.between(venc, hoy));
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
    }

    /** Mora acumulada en soles: días × S/ 3.00. */
    public static double moraSoles(int diasMora) {
        return diasMora * MORA_DIARIA_SOLES;
    }

    // ── Cuotas ───────────────────────────────────────────────────────────────

    /**
     * true si alguna cuota no pagada vence exactamente hoy (día 0).
     * Usada para el recordatorio amistoso antes de que exista mora.
     */
    public static boolean tienesCuotaQueVenceHoy(CasoCobranzaDocument caso, LocalDate hoy) {
        if (caso.getCronograma() == null) return false;
        return caso.getCronograma().stream()
                .filter(c -> !"PAGADA".equalsIgnoreCase(c.getEstado())
                        && c.getFechaVencimiento() != null)
                .anyMatch(c -> {
                    try {
                        return LocalDate.parse(c.getFechaVencimiento()).equals(hoy);
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    /**
     * Primera cuota no pagada cuya fecha de vencimiento cae en [desde, hasta].
     * Usada para recordatorio de pre-vencimiento (día -1).
     * Devuelve null si no hay ninguna.
     */
    public static CuotaCronogramaDocument proximaCuotaEnRango(
            CasoCobranzaDocument caso, LocalDate desde, LocalDate hasta) {
        if (caso.getCronograma() == null) return null;
        return caso.getCronograma().stream()
                .filter(c -> !"PAGADA".equalsIgnoreCase(c.getEstado())
                        && c.getFechaVencimiento() != null)
                .filter(c -> {
                    try {
                        LocalDate fv = LocalDate.parse(c.getFechaVencimiento());
                        return !fv.isBefore(desde) && !fv.isAfter(hasta);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Monto de la primera cuota que vence hoy o que ya venció y no está pagada.
     * Devuelve 0.0 si no hay ninguna.
     */
    public static double montoPrimeraCuotaImpaga(CasoCobranzaDocument caso, LocalDate hoy) {
        if (caso.getCronograma() == null) return 0.0;
        return caso.getCronograma().stream()
                .filter(c -> !"PAGADA".equalsIgnoreCase(c.getEstado())
                        && c.getFechaVencimiento() != null)
                .filter(c -> {
                    try {
                        return !LocalDate.parse(c.getFechaVencimiento()).isAfter(hoy);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .mapToDouble(c -> c.getMonto() != null ? c.getMonto() : 0.0)
                .findFirst()
                .orElse(0.0);
    }

    // ── Nivel ─────────────────────────────────────────────────────────────────

    /**
     * Nivel de estrategia según días de mora.
     * Devuelve AL_DIA si diasMora == 0.
     */
    public static String calcularNivel(int diasMora) {
        if (diasMora >= 61) return NivelEstrategia.JUDICIAL.name();
        if (diasMora >= 31) return NivelEstrategia.MORA_CRITICA.name();
        if (diasMora >= 16) return NivelEstrategia.MORA_MEDIA.name();
        if (diasMora >= 1)  return NivelEstrategia.MORA_TEMPRANA.name();
        return NivelEstrategia.AL_DIA.name();
    }

    // ── Formato ───────────────────────────────────────────────────────────────

    public static String fmt(double monto) {
        return String.format("S/ %.2f", monto);
    }
}