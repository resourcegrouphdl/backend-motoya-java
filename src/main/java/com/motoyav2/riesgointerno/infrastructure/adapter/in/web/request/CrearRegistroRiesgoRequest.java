package com.motoyav2.riesgointerno.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CrearRegistroRiesgoRequest {

    private String dniRegistrado;

    @NotBlank
    private String nombreRegistrado;

    private List<String> telefonos;

    @NotNull
    private String tipoSujeto;

    @NotNull
    private String nivelRiesgo;

    @NotNull
    private String estadoRegistro;

    @NotNull
    private String tipoRiesgo;

    private String contratoIdRelacionado;
    private String solicitudIdRelacionado;
    private Double montoDeudaPendiente;

    /** ISO date string YYYY-MM-DD */
    private String fechaIncidente;

    @NotBlank
    private String descripcion;

    private List<String> evidencias;
    private List<String> condicionesRehabilitacion;
}
