package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

public record EvaluarDocumentosRequest(
        Double scoreDocumental,
        String observaciones,
        String nuevoEstado       // nullable — si se quiere cambiar estado también
) {}
