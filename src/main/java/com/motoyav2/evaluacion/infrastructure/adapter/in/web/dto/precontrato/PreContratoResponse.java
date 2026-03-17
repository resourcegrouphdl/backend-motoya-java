package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Pre-llenado del formulario de creación de contrato.
 * Estructura idéntica a CrearContratoManualRequest (módulo contrato)
 * más metadatos de la solicitud para validación frontend.
 *
 * Flujo esperado:
 *   1. Frontend llama GET /api/v1/expediente/{solicitudId}/pre-contrato
 *   2. Frontend muestra datos pre-llenados para revisión
 *   3. Usuario confirma → Frontend llama POST /api/v1/contract con los datos
 */
@Getter
@Builder
public class PreContratoResponse {

    /** Estado de la solicitud al momento de generar el pre-contrato. */
    private final String estadoSolicitud;
    /** Número de solicitud legible (MDCR-...). */
    private final String numeroSolicitud;
    /** Indica si la solicitud está en un estado que permite crear contrato. */
    private final boolean puedeCrearContrato;
    /** Mensaje de advertencia si hay datos faltantes. */
    private final String advertencia;

    private final TitularPreDto titular;
    private final FiadorPreDto fiador;          // null si no tiene fiador
    private final TiendaPreDto tienda;
    private final FinancierosPreDto datosFinancieros;

    /** evaluacionId para pasar al POST /api/v1/contract. */
    private final String evaluacionId;

    // ── Nested DTOs ──────────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class TitularPreDto {
        private final String nombres;
        private final String apellidos;
        private final String tipoDocumento;
        private final String numeroDocumento;
        private final String telefono;
        private final String email;
        private final String direccion;
        private final String distrito;
        private final String provincia;
        private final String departamento;
    }

    @Getter
    @Builder
    public static class FiadorPreDto {
        private final String nombres;
        private final String apellidos;
        private final String tipoDocumento;
        private final String numeroDocumento;
        private final String telefono;
        private final String email;
        private final String direccion;
        private final String distrito;
        private final String provincia;
        private final String departamento;
        private final String parentesco;
    }

    @Getter
    @Builder
    public static class TiendaPreDto {
        private final String tiendaId;
        private final String nombreTienda;
        private final String direccion;
        private final String ciudad;
    }

    @Getter
    @Builder
    public static class FinancierosPreDto {
        private final BigDecimal precioVehiculo;
        private final BigDecimal cuotaInicial;
        private final BigDecimal montoFinanciado;
        private final BigDecimal tasaInteresAnual;
        private final Integer numeroCuotas;
        private final BigDecimal cuotaMensual;    // monto quincenal (campo mal nombrado en contrato)
        private final String marcaVehiculo;
        private final String modeloVehiculo;
        private final String anioVehiculo;
        private final String colorVehiculo;
        private final String numeroMotor;         // null — no está en solicitud
        private final String numeroChasis;        // null — no está en solicitud
        private final String placa;               // null — no está en solicitud
    }
}
