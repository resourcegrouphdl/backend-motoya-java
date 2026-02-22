package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import lombok.Data;

@Data
public class DatosFinancierosEmbedded {
    private Double precioVehiculo;
    private Double cuotaInicial;
    private Double montoFinanciado;
    private Double tasaInteresAnual;
    private Integer numeroCuotas;
    private Double cuotaMensual;
    private String marcaVehiculo;
    private String modeloVehiculo;
    private String anioVehiculo;
    private String colorVehiculo;
    private String numeroMotor;
    private String numeroChasis;
    private String placa;
}
