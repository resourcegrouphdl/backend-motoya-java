package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record IngresarSolicitudRequest(

        @NotNull @Valid ClienteRequest titular,
        @Valid ClienteRequest fiador,
        @NotNull @Size(min = 1) @Valid List<ReferenciaRequest> referencias,
        @NotNull @Valid VehiculoRequest vehiculo,
        @NotNull @Valid FinanciamientoRequest financiamiento,
        @NotNull @Valid VendedorRequest vendedor,
        String mensajeOpcional

) {
    public record ClienteRequest(
            @NotBlank String documentType,
            @NotBlank String documentNumber,
            @NotBlank String nombres,
            @NotBlank String apellidoPaterno,
            @NotBlank String apellidoMaterno,
            String estadoCivil,
            String email,
            String fechaNacimiento,
            String departamento,
            String provincia,
            String distrito,
            String direccion,
            String ubicacionGPSCasa,
            String telefono1,
            String telefono2,
            String ocupacion,
            String rangoIngresos,
            String tipoVivienda,
            String licenciaConducir,
            String numeroLicencia
    ) {}

    public record ReferenciaRequest(
            @NotBlank String nombre,
            @NotBlank String apellidos,
            @NotBlank String telefono,
            @NotBlank String parentesco
    ) {}

    public record VehiculoRequest(
            @NotBlank String marca,
            @NotBlank String modelo,
            String color,
            String anio
    ) {}

    public record FinanciamientoRequest(
            @NotNull Double precioCompraMoto,
            @NotNull Double inicial,
            @NotNull Integer plazoQuincenas,
            Double montoCuota
    ) {}

    public record VendedorRequest(
            @NotBlank String id,
            @NotBlank String nombre,
            String tienda
    ) {}
}
