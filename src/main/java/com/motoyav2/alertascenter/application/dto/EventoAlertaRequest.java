package com.motoyav2.alertascenter.application.dto;

import com.motoyav2.alertascenter.domain.model.SubTipoAlerta;
import com.motoyav2.alertascenter.domain.model.TipoAlerta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EventoAlertaRequest(
        @NotNull TipoAlerta tipo,
        SubTipoAlerta subTipo,
        @NotBlank String fuenteId
) {}
