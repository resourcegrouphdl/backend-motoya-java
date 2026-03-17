package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ScoreResultDto {

    private final double scoreFinal;
    private final double scoreDocumental;
    private final Double scoreGarantes;           // null si no hay fiador
    private final double scoreEntrevista;
    private final double scoreReferencias;
    private final boolean tieneFiador;
    private final String descripcionPonderacion;

    // detalle documental
    private final double completitudDocumental;   // % docs subidos
    private final double aprobacionDocumental;    // % docs aprobados
    private final int docsSubidos;
    private final int docsAprobados;
    private final int docsRequeridos;
    private final Map<String, String> estadoPorDocumento;
    private final boolean licenciaVigente;

    // detalle entrevista
    private final boolean entrevistaRealizada;
    private final String recomendacionEntrevista;
    private final int alertasCriticas;
    private final int alertasAltas;

    // detalle referencias
    private final int referenciasVerificadas;
    private final int referenciasRechazadas;

    // capacidad de pago
    private final double ingresoMensualEstimado;
    private final double cuotaMensual;
    private final double ratioCuotaIngreso;       // cuota/ingreso
    private final boolean cumpleRatioCuota;       // < 0.35
    private final String nivelCapacidadPago;      // ALTA | MEDIA | BAJA | INSUFICIENTE
    private final boolean ingresoEstimado;        // si se usó rangoIngresos
}
