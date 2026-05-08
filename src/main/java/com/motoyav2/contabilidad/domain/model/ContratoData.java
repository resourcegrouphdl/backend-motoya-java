package com.motoyav2.contabilidad.domain.model;

/**
 * Datos financieros del contrato necesarios para calcular el desglose.
 */
public record ContratoData(
        String contratoId,
        String tiendaId,
        double montoFinanciado,
        double tasaInteres,
        int numeroCuotas
) {
    /** Deriva la tasa plana desde el número de quincenas si tasaInteres no fue guardada. */
    public double tasaEfectiva() {
        if (tasaInteres > 0) return tasaInteres;
        return switch (numeroCuotas) {
            case 16 -> 0.2626;
            case 20 -> 0.3263;
            case 24 -> 0.3919;
            default -> 0.2626;
        };
    }
}
