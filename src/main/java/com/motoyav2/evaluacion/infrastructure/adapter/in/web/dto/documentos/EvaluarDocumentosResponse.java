package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class EvaluarDocumentosResponse {
    private final boolean success;
    private final String clienteId;
    private final String solicitudId;
    private final String estadoValidacionDocumentos;   // nuevo estado global
    private final double scoreDocumental;              // score recalculado
    private final int docsAprobados;
    private final int docsObservados;
    private final int docsRequeridos;
    private final Map<String, String> estadoPorDocumento;
    private final String mensaje;
}
