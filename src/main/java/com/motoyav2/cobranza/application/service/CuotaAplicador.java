package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;

import java.util.Comparator;
import java.util.List;

/**
 * Utilidad de dominio para aplicar pagos al cronograma de cuotas.
 * Sin dependencias de Spring — se puede llamar desde cualquier servicio del módulo.
 *
 * Reglas:
 *  - Si numeroCuota está especificado (modo migración) → marca solo esa cuota.
 *  - Si no → aplica cronológicamente contra las más antiguas primero.
 *  - Tolerancia del 5 %: cubre redondeos sin bloquear la marca.
 */
final class CuotaAplicador {

    private static final double TOLERANCIA = 0.95;

    private CuotaAplicador() {}

    /**
     * Aplica el monto pagado al cronograma marcando cuotas como PAGADA.
     *
     * @param cronograma           Lista embebida en CasoCobranzaDocument
     * @param montoPagado          Monto que se está aplicando
     * @param fechaPago            ISO date YYYY-MM-DD del pago efectivo
     * @param numeroCuotaEspecifica Si no es null, solo marca esa cuota (migración/admin)
     * @return cantidad de cuotas marcadas en esta llamada
     */
    static int aplicar(List<CuotaCronogramaDocument> cronograma,
                       double montoPagado,
                       String fechaPago,
                       Integer numeroCuotaEspecifica) {

        if (cronograma == null || cronograma.isEmpty()) return 0;

        if (numeroCuotaEspecifica != null) {
            return aplicarACuotaEspecifica(cronograma, fechaPago, numeroCuotaEspecifica);
        }

        return aplicarCronologicamente(cronograma, montoPagado, fechaPago);
    }

    /**
     * Retorna el monto de la próxima cuota no pagada (para calcular montoEsperado del voucher).
     */
    static Double montoProximaCuota(List<CuotaCronogramaDocument> cronograma) {
        if (cronograma == null) return null;
        return cronograma.stream()
                .filter(c -> !"PAGADA".equalsIgnoreCase(c.getEstado()))
                .filter(c -> c.getFechaVencimiento() != null)
                .min(Comparator.comparing(CuotaCronogramaDocument::getFechaVencimiento))
                .map(CuotaCronogramaDocument::getMonto)
                .orElse(null);
    }

    // ── privados ─────────────────────────────────────────────────────────────

    private static int aplicarACuotaEspecifica(List<CuotaCronogramaDocument> cronograma,
                                                String fechaPago,
                                                int numeroCuota) {
        for (CuotaCronogramaDocument c : cronograma) {
            Integer num = c.getCuotaNum() != null ? c.getCuotaNum() : c.getCuota();
            if (numeroCuota == num && !"PAGADA".equalsIgnoreCase(c.getEstado())) {
                c.setEstado("PAGADA");
                c.setFechaPago(fechaPago);
                return 1;
            }
        }
        return 0;
    }

    private static int aplicarCronologicamente(List<CuotaCronogramaDocument> cronograma,
                                                double montoPagado,
                                                String fechaPago) {
        List<CuotaCronogramaDocument> pendientes = cronograma.stream()
                .filter(c -> !"PAGADA".equalsIgnoreCase(c.getEstado()))
                .filter(c -> c.getFechaVencimiento() != null && c.getMonto() != null && c.getMonto() > 0)
                .sorted(Comparator.comparing(CuotaCronogramaDocument::getFechaVencimiento))
                .toList();

        double restante = montoPagado;
        int count = 0;

        for (CuotaCronogramaDocument c : pendientes) {
            if (restante <= 0) break;
            if (restante >= c.getMonto() * TOLERANCIA) {
                c.setEstado("PAGADA");
                c.setFechaPago(fechaPago);
                restante -= c.getMonto();
                count++;
            }
        }
        return count;
    }
}
