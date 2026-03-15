package com.motoyav2.migracion.application.dto;

import java.util.List;

public record EjecutarLoteRequest(
        /** null = procesar todos los COMPLETO */
        List<String> ids
) {}
