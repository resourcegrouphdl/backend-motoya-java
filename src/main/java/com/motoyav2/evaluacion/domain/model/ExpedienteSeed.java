package com.motoyav2.evaluacion.domain.model;

import java.util.List;

public record ExpedienteSeed(
    Evaluacion evaluacion,
    List<String> referenciasIds,
    String montoCuota,
    String plazoQuincenas,
    String precioCompraMoto
) {}
