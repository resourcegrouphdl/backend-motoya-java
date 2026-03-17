package com.motoyav2.evaluacion.domain.model.riesgo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agregado del perfil de riesgo de un expediente.
 * Consolida todos los flags y determina el nivel general.
 */
@Getter
@Builder
public class PerfilRiesgo {

    private final NivelRiesgo nivelGeneral;
    private final List<FlagRiesgo> flags;
    private final int totalFlags;
    private final int flagsCriticos;
    private final int flagsAltos;
    private final int flagsMedios;

    /** Recomendación automática basada en el perfil */
    public String recomendacionAutomatica() {
        return switch (nivelGeneral) {
            case BAJO   -> "Proceder con evaluación estándar";
            case MEDIO  -> "Requiere revisión detallada de factores de riesgo";
            case ALTO   -> "Requiere aprobación de supervisor";
            case CRITICO -> "Caso candidato a rechazo — escalar a comité";
        };
    }

    public boolean tieneFlag(FlagRiesgo.TipoFlag tipo) {
        return flags.stream().anyMatch(f -> f.getTipo() == tipo);
    }

    public static PerfilRiesgo sinFlags() {
        return PerfilRiesgo.builder()
                .nivelGeneral(NivelRiesgo.BAJO)
                .flags(List.of())
                .totalFlags(0).flagsCriticos(0).flagsAltos(0).flagsMedios(0)
                .build();
    }
}
