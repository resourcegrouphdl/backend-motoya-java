package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.Referencia;
import com.motoyav2.evaluacion.domain.model.SemaforoReferencias;

import java.util.List;

/**
 * Servicio de dominio puro: calcula el semáforo de referencias a partir del
 * estado actual de la lista. No tiene dependencias de infraestructura.
 *
 * Reglas:
 *   ROJO    → al menos 1 referencia con estadoVerificacion = "rechazado"
 *   VERDE   → 2 o más referencias con estadoVerificacion = "verificado"
 *   AMARILLO → cualquier otro caso (pendientes, dudosas, mixtas)
 */
public final class CalculadoraSemaforoService {

    private static final int MINIMO_POSITIVAS = 2;

    private CalculadoraSemaforoService() {}

    public static SemaforoReferencias calcular(List<Referencia> referencias) {
        if (referencias == null || referencias.isEmpty()) return SemaforoReferencias.AMARILLO;

        long rechazadas = referencias.stream()
                .filter(r -> "rechazado".equals(r.getEstadoVerificacion()))
                .count();

        if (rechazadas > 0) return SemaforoReferencias.ROJO;

        long verificadas = referencias.stream()
                .filter(r -> "verificado".equals(r.getEstadoVerificacion()))
                .count();

        if (verificadas >= MINIMO_POSITIVAS) return SemaforoReferencias.VERDE;

        return SemaforoReferencias.AMARILLO;
    }
}
