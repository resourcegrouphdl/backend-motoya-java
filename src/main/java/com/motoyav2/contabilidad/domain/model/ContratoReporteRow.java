package com.motoyav2.contabilidad.domain.model;

public record ContratoReporteRow(
        String numeroContrato,
        String estado,
        String tiendaNombre,
        String clienteNombre,
        String clienteTipoDocumento,
        String clienteNumeroDocumento,
        String clienteTelefono,
        String marcaModelo,
        Double precioVehiculo,
        Double cuotaInicial,
        Double montoFinanciado,
        Integer numeroCuotas,
        Double cuotaMensual,
        Double tasaInteresAnual,
        String fechaCierre
) {}
