package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PerfilRiesgoDto {

    private final String nivelGeneral;        // BAJO | MEDIO | ALTO | CRITICO
    private final int totalFlags;
    private final int flagsCriticos;
    private final int flagsAltos;
    private final int flagsMedios;
    private final String recomendacionAutomatica;
    private final List<FlagRiesgoDto> flags;

    @Getter
    @Builder
    public static class FlagRiesgoDto {
        private final String tipo;
        private final String severidad;
        private final String descripcion;
        private final String origen;
    }
}
