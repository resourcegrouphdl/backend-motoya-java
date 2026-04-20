package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ActualizarPermisosRequest(
        @NotNull List<String> modulos
) {}
