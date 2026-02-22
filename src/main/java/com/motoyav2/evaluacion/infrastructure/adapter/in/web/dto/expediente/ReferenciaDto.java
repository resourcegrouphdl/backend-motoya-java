package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferenciaDto {

    private String id;
    private Integer numero;
    private String nombre;
    private String apellidos;
    private String nombreCompleto;
    private String telefono;
    private String parentesco;
    private Boolean verificada;
    private String fechaVerificacion;
    private String verificadoPor;
    private String resultadoVerificacion;
    private String observaciones;
    private Double scoreVerificacion;
}
