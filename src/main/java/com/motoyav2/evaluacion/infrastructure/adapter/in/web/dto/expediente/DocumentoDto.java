package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentoDto {

    private String id;
    private String tipo;
    private String nombre;
    private String url;
    private String tipoPersona;
    private Boolean validado;
    private String observaciones;
    private String fechaSubida;
}
