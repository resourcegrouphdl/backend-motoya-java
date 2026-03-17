package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto;

import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Respuesta del expediente completo.
 * Estructura alineada con el contrato TypeScript ExpedienteCompleto.
 * Agrega scores calculados en backend y perfil de riesgo.
 */
@Getter
@Builder
public class ExpedienteCompletoResponse {

    // ── Datos crudos (del contrato TypeScript) ───────────────────────────────
    private final SolicitudDto solicitud;
    private final ClienteCompletoDto titular;
    private final ClienteCompletoDto fiador;        // null si no existe
    private final VehiculoCompletoDto vehiculo;
    private final List<ReferenciaCompletoDto> referencias;
    private final EntrevistaCompletoDto evaluacionEntrevista;  // del titular

    // ── Meta calculada en backend ────────────────────────────────────────────
    private final boolean datosCompletos;
    private final AsesorDto asesorAsignado;         // null si no asignado
    private final ScoreResultDto scores;            // scores calculados
    private final PerfilRiesgoDto perfilRiesgo;     // flags de riesgo
    private final String solicitudId;               // formularioId

    // ─────────────────────────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class SolicitudDto {
        private final String id;
        private final String numeroSolicitud;
        private final String estado;
        private final String prioridad;
        private final String titularId;
        private final String fiadorId;
        private final String vehiculoId;
        private final List<String> referenciasIds;

        // financieros legacy
        private final Long precioCompraMoto;
        private final Long inicial;
        private final Long montoCuota;
        private final Object plazoQuincenas;    // string o number (legacy quirk)

        // financieros nuevos
        private final Map<String, Object> datosFinancieros;

        // vendedor / tienda
        private final VendedorSimpleDto vendedor;
        private final String mensajeOpcional;
        private final String asesorAsignadoId;

        // scores guardados (del frontend, pueden diferir de los calculados)
        private final Double scoreDocumental;
        private final Double scoreGarantes;
        private final Double scoreEntrevista;
        private final Double scoreFinal;

        // decisión
        private final String decisionFinal;
        private final Double montoAprobado;
        private final String motivoRechazo;
        private final String motivoDecision;
        private final List<String> condicionesAprobacion;

        // documentos generados
        private final Boolean certificadoGenerado;
        private final String urlCertificado;
        private final Boolean contratoGenerado;
        private final String urlContrato;

        private final String observacionesGenerales;
        private final String createdAt;
        private final String updatedAt;
    }

    @Getter
    @Builder
    public static class VendedorSimpleDto {
        private final String id;
        private final String nombre;
        private final String tienda;
        private final String email;
        private final String telefono;
    }

    @Getter
    @Builder
    public static class ClienteCompletoDto {
        private final String id;
        private final String tipo;
        private final String nombres;
        private final String apellidoPaterno;
        private final String apellidoMaterno;
        private final String nombreCompleto;
        private final String documentType;
        private final String documentNumber;
        private final String nacionalidad;
        private final String sexo;
        private final String fechaNacimiento;   // "YYYY-MM-DD"
        private final String estadoCivil;
        private final String cargasFamiliares;
        private final Integer cargasFamiliaresNum;
        private final String email;
        private final String telefono1;
        private final String telefono2;
        private final String departamento;
        private final String provincia;
        private final String distrito;
        private final String direccion;
        private final String tipoDeVivienda;
        private final String antiguedadDomiciliaria;
        private final String ocupacion;
        private final String tipoTrabajo;
        private final String nombreEmpresa;
        private final String ingresoMensual;
        private final Double ingresoMensualNum;
        private final String rangoIngresos;
        private final String licenciaDeConducir;
        private final String perfilSentinel;
        private final Double totalDeudaBancos;
        private final Double totalOtrasDeudas;
        private final Boolean tienePapeletasPendientes;
        private final Double totalDeudaPapeletas;
        private final String estadoValidacionDocumentos;
        private final Boolean datosVerificados;
        private final String observacionesEvaluador;
        private final Map<String, Object> archivos;         // tipoDoc → URL
        private final Map<String, Object> evaluacionDocumentos;
        private final String createdAt;
        private final String updatedAt;
    }

    @Getter
    @Builder
    public static class VehiculoCompletoDto {
        private final String id;
        private final String marca;
        private final String modelo;
        private final String anio;              // string (quirk de Firestore)
        private final String color;
        private final Double precioReferencial;
        private final Double cilindrada;
        private final String createdAt;
    }

    @Getter
    @Builder
    public static class ReferenciaCompletoDto {
        private final String id;
        private final Integer numero;
        private final String nombre;
        private final String apellidos;
        private final String nombreCompleto;
        private final String telefono;
        private final String parentesco;
        private final String estadoVerificacion;
        private final String resultadoContacto;
        private final Double scoreVerificacion;
        private final String calificacion;
        private final String observaciones;
        private final String actitudDuranteContacto;
        private final Boolean rechazada;
    }

    @Getter
    @Builder
    public static class EntrevistaCompletoDto {
        private final String solicitudId;
        private final String entrevistadorId;
        private final String entrevistadorNombre;
        private final String modalidad;
        private final String puntualidad;
        private final Integer presentacionPersonal;
        private final Integer actitudColaboracion;
        private final Integer coherenciaRespuestas;
        private final Integer nivelConfianza;
        private final String observacionesCliente;
        private final String observacionesDomicilio;
        private final String observacionesCapacidadPago;
        private final List<String> hallazgosPositivos;
        private final List<String> hallazgosNegativos;
        private final List<AlertaEntrevistaDto> alertas;
        private final Integer scoreEntrevista;          // score guardado (frontend)
        private final String recomendacion;
        private final String motivoRecomendacion;
        private final Boolean esBorrador;
    }

    @Getter
    @Builder
    public static class AlertaEntrevistaDto {
        private final String tipo;
        private final String descripcion;
        private final String severidad;
        private final String timestamp;
    }
}
