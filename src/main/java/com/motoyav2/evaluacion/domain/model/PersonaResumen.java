package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Datos básicos de una persona para autocompletar el formulario del vendedor,
 * junto con las alertas de riesgo crediticio encontradas.
 */
@Value
@Builder
public class PersonaResumen {
    String documentType;
    String documentNumber;
    String nombres;
    String apellidoPaterno;
    String apellidoMaterno;
    String email;
    String telefono1;
    String telefono2;
    String estadoCivil;
    String fechaNacimiento;
    String departamento;
    String provincia;
    String distrito;
    String direccion;
    String ocupacion;
    String rangoIngresos;
    String tipoVivienda;
    String licenciaConducir;
    String numeroLicencia;
    /** Alertas crediticias para este documento. Vacía si no hay historial. */
    List<AlertaCredito> alertas;
}
