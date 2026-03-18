package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import java.util.Map;

public record ContratoRequest(
        Map<String, Object> camposAdicionales
) {}
