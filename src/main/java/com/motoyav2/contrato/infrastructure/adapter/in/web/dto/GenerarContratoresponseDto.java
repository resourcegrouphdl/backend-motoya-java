package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import java.util.List;

public record GenerarContratoresponseDto(
    String message,
    List<DocumentoGeneradoAPIDto> documentosGenerados
) {
}
