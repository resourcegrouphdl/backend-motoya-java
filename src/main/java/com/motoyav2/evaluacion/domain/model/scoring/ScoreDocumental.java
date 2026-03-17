package com.motoyav2.evaluacion.domain.model.scoring;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Value Object que representa el score documental calculado para un cliente.
 * Score range: 0–100.
 */
@Getter
@Builder
public class ScoreDocumental {

    private final double valor;           // 0-100
    private final double completitud;     // % de docs requeridos presentes
    private final double aprobacion;      // % de docs presentes que están aprobados
    private final int docsSubidos;
    private final int docsAprobados;
    private final int docsObservados;
    private final int docsRequeridos;
    private final Map<String, String> estadoPorDocumento; // tipoDoc → estado
    private final boolean licenciaVigente;
    private final boolean documentoIdentidadVigente;

    public static ScoreDocumental cero() {
        return ScoreDocumental.builder()
                .valor(0).completitud(0).aprobacion(0)
                .docsSubidos(0).docsAprobados(0).docsObservados(0).docsRequeridos(0)
                .estadoPorDocumento(Map.of())
                .licenciaVigente(false).documentoIdentidadVigente(false)
                .build();
    }
}
