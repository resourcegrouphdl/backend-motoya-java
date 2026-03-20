package com.motoyav2.evaluacion.application.command;

import java.util.List;

public record IngresarSolicitudCommand(
        ClienteData titular,
        ClienteData fiador,           // null si no aplica
        List<ReferenciaData> referencias,
        VehiculoData vehiculo,
        FinanciamientoData financiamiento,
        VendedorData vendedor,
        String mensajeOpcional
) {
    public record ClienteData(
            String documentType,
            String documentNumber,
            String nombres,
            String apellidoPaterno,
            String apellidoMaterno,
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

    public record ReferenciaData(
            String nombre,
            String apellidos,
            String telefono,
            String parentesco
    ) {}

    public record VehiculoData(
            String marca,
            String modelo,
            String color,
            String anio
    ) {}

    public record FinanciamientoData(
            Double precioCompraMoto,
            Double inicial,
            Integer plazoQuincenas,
            Double montoCuota
    ) {}

    public record VendedorData(
            String id,
            String nombre,
            String tienda
    ) {}
}
