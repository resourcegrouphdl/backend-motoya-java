package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Parámetros de entrada para una simulación de crédito.
 */
@Value
@Builder
public class ParametrosSimulacion {

    /** Precio del vehículo en soles (capital base) */
    BigDecimal precioVehiculo;

    /** SOAT del vehículo en soles (costo financiable, puede ser 0) */
    BigDecimal soat;

    /** Inicial del cliente (null o menor que la mínima → se usa la mínima calculada) */
    BigDecimal inicial;

    /** Número de cuotas (semanas si WEEKLY, meses si MONTHLY) */
    int numeroCuotas;

    /** Frecuencia de pago: WEEKLY o MONTHLY */
    FrequenciaPago frecuencia;

    /**
     * TEA override (null → usa la TEA del plazo configurado o teaDefault).
     * Permite experimentar sin modificar la config persistida.
     */
    BigDecimal teaOverride;

    /** Si true, incluye seguro de desgravamen en cada cuota */
    boolean incluirSeguro;

    /**
     * Comisión de desembolso (null → sin comisión).
     * Si financiada=true: se suma al capital.
     * Si financiada=false: se descuenta del efectivo recibido → afecta TCEA.
     */
    ComisionConfig comision;
}