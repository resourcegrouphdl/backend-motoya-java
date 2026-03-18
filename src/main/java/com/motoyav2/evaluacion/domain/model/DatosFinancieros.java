package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Value
@Builder(toBuilder = true)
public class DatosFinancieros {
    BigDecimal montoVehiculo;
    BigDecimal soatCostosNotariales;
    BigDecimal costoTotal;
    BigDecimal inicial;
    BigDecimal montoFinanciar;
    Integer numeroCuotasQuincenales;
    BigDecimal montoCuotaQuincenal;
    BigDecimal montoAbonarDealer;
    BigDecimal totalAPagar;
    BigDecimal porcentajeInicial;

    /** Recalcula cuotas aplicando ajustes del evaluador. */
    public DatosFinancieros recalcular(BigDecimal nuevaInicial, int nuevoPlazo) {
        BigDecimal mFinanciar = costoTotal.subtract(nuevaInicial);
        BigDecimal cuota = mFinanciar.divide(BigDecimal.valueOf(nuevoPlazo), 2, RoundingMode.HALF_UP);
        BigDecimal total = nuevaInicial.add(cuota.multiply(BigDecimal.valueOf(nuevoPlazo)));
        BigDecimal pct = costoTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : nuevaInicial.divide(costoTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return this.toBuilder()
                .inicial(nuevaInicial)
                .montoFinanciar(mFinanciar)
                .numeroCuotasQuincenales(nuevoPlazo)
                .montoCuotaQuincenal(cuota)
                .totalAPagar(total)
                .porcentajeInicial(pct)
                .build();
    }
}
