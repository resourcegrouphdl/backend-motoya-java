package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Aggregate root del módulo de evaluación.
 * Agrega Solicitud + entidades relacionadas y encapsula reglas de negocio cross-entidad.
 */
@Value
@Builder
public class Expediente {
    Solicitud solicitud;
    Cliente titular;
    Cliente fiador;             // null si no hay fiador
    Vehiculo vehiculo;
    List<Referencia> referencias;
    Usuario asesorAsignado;     // null si no hay asesor aún

    /** Al menos 2 referencias con estadoVerificacion = 'verificado' */
    public boolean cumpleRequisitoReferencias() {
        if (referencias == null) return false;
        return referencias.stream().filter(Referencia::estaVerificada).count() >= 2;
    }

    /** Ambas entrevistas finalizadas (esBorrador == false) */
    public boolean entrevistasCompletas() {
        boolean titularFinalizado = titular != null && titular.entrevistaFinalizada();
        boolean fiadorOk = fiador == null || fiador.entrevistaFinalizada();
        return titularFinalizado && fiadorOk;
    }

    /** Ambas entrevistas aprobadas */
    public boolean ambosAprobadosEnEntrevista() {
        boolean titularAprobado = titular != null && titular.entrevistaAprobada();
        boolean fiadorAprobado = fiador == null || fiador.entrevistaAprobada();
        return titularAprobado && fiadorAprobado;
    }

    public boolean tieneFiador() {
        return fiador != null;
    }

    public boolean datosCompletos() {
        return solicitud != null
                && titular != null
                && vehiculo != null
                && referencias != null && !referencias.isEmpty();
    }
}
