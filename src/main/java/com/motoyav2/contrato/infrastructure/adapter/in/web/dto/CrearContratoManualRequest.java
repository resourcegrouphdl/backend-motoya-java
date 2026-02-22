package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CrearContratoManualRequest(
        @Valid @NotNull TitularDto titular,
        @Valid FiadorDto fiador,
        @Valid @NotNull TiendaDto tienda,
        @Valid @NotNull FinancierosDto datosFinancieros,
        String evaluacionId
) {
    public record TitularDto(
            @NotBlank String nombres,
            @NotBlank String apellidos,
            @NotBlank String tipoDocumento,
            @NotBlank String numeroDocumento,
            String telefono,
            String email,
            String direccion,
            String distrito,
            String provincia,
            String departamento
    ) {}

    public record FiadorDto(
            @NotBlank String nombres,
            @NotBlank String apellidos,
            @NotBlank String tipoDocumento,
            @NotBlank String numeroDocumento,
            String telefono,
            String email,
            String direccion,
            String distrito,
            String provincia,
            String departamento,
            String parentesco
    ) {}

    public record TiendaDto(
            @NotBlank String tiendaId,
            @NotBlank String nombreTienda,
            String direccion,
            String ciudad
    ) {}

    public record FinancierosDto(
            @NotNull @Positive BigDecimal precioVehiculo,
            @NotNull @Positive BigDecimal cuotaInicial,
            @NotNull @Positive BigDecimal montoFinanciado,
            @NotNull @PositiveOrZero BigDecimal tasaInteresAnual,
            @NotNull @Positive Integer numeroCuotas,
            @NotNull @Positive BigDecimal cuotaMensual,
            @NotBlank String marcaVehiculo,
            @NotBlank String modeloVehiculo,
            String anioVehiculo,
            String colorVehiculo,
            String numeroMotor,
            String numeroChasis,
            String placa
    ) {}
}
