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
    String licenciaEstado;          // "VIGENTE" | "VENCIDA" | ...
    String licenciaVencimiento;     // "dd/MM/yyyy"
    String licenciaRestricciones;   // "SIN RESTRICCIONES" o descripción
    Boolean licenciaTieneRestricion; // true si hay restricción real

    // ── datos demográficos (solo DNI — CEE no los incluye) ────────────────
    String apiSexo;
    String apiFechaNacimiento;

    // ── comparaciones con datos del cliente ───────────────────────────────
    Boolean coincideNombres;
    Boolean coincideApellidos;
    Boolean coincideSexo;
    Boolean coincideFechaNacimiento;
    // coincideUbicacion eliminado: la dirección del documento puede diferir de la
    // residencia actual (alquiler, mudanza) — se muestra como referencia, no como alerta.
    Boolean licenciaVigente;
    Boolean tieneConducir;

    // ── auto-relleno (campo vacío en BD → se llenó con dato de la API) ────
    Boolean autorellenoSexo;
    Boolean autorellenoFechaNacimiento;

    // ── metadata ──────────────────────────────────────────────────────────
    String verificadoPor;
    String observaciones;
    boolean exitoso;         // false si la API no respondió
}
