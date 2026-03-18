package com.motoyav2.evaluacion.application.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Result returned to the controller and stored in Firestore
 * under clientes_v1/{clienteId}.verificacionIdentidad
 */
@Value
@Builder
public class VerificacionIdentidadResult {

    // ── datos del documento ───────────────────────────────────────────────
    String documentType;
    String documentNumber;

    // ── respuesta de la API para DNI / CEE ────────────────────────────────
    String apiNombres;
    String apiApellidoPaterno;
    String apiApellidoMaterno;
    String apiDireccion;
    String apiUbigeo;
    String apiDepartamento;
    String apiProvincia;
    String apiDistrito;

    // ── respuesta licencia (solo DNI) ─────────────────────────────────────
    String licenciaNumero;
    String licenciaCategoria;
    String licenciaEstado;
    String licenciaVencimiento;

    // ── comparaciones con datos del cliente ───────────────────────────────
    Boolean coincideNombres;
    Boolean coincideApellidos;
    // coincideUbicacion eliminado: la dirección del documento puede diferir de la
    // residencia actual (alquiler, mudanza) — se muestra como referencia, no como alerta.
    Boolean licenciaVigente;
    Boolean tieneConducir;

    // ── metadata ──────────────────────────────────────────────────────────
    String verificadoPor;
    String observaciones;
    boolean exitoso;         // false si la API no respondió
}
