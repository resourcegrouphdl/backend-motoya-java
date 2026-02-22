package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DatosFinancierosDto {

    private Double montoVehiculo;
    private Double soatCostosNotariales;
    private Double costoTotal;
    private Double inicialOriginal;
    private Double montoFinanciarOriginal;
    private Integer numeroCuotasQuincenales;
    private Double montoCuotaQuincenal;
    private Double inicialAjustada;
    private Double montoFinanciarAjustado;
    private Double montoCuotaAjustada;
    private Double porcentajeInicial;
    private Double relacionCuotaIngreso;
    private String capacidadPago;
}
