package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Proyección del objeto evaluacionEntrevista embebido en clientes_v1.
 * Parsed desde Map<String, Object> en el mapper.
 */
@Getter
@Builder
public class EntrevistaCompleta {

    private final String solicitudId;
    private final String entrevistadorId;
    private final String entrevistadorNombre;

    // modalidad
    private final String modalidad;     // videollamada | presencial | telefonica
    private final String puntualidad;   // puntual | retraso_leve | retraso_significativo | no_asistio

    // factores cualitativos (1-5)
    private final Integer presentacionPersonal;
    private final Integer actitudColaboracion;
    private final Integer coherenciaRespuestas;
    private final Integer nivelConfianza;

    // observaciones
    private final String observacionesCliente;
    private final String observacionesFiador;
    private final String observacionesDomicilio;
    private final String observacionesCapacidadPago;

    // hallazgos
    private final List<String> hallazgosPositivos;
    private final List<String> hallazgosNegativos;
    private final List<AlertaEntrevista> alertas;

    // decisión
    private final Integer scoreEntrevista;  // 0-100 (guardado por el frontend, puede diferir del calculado)
    private final String recomendacion;     // aprobar | rechazar | condicional | requiere_comite | revisar
    private final String motivoRecomendacion;
    private final List<String> condiciones;

    private final Boolean esBorrador;
    private final String fechaInicio;
    private final String fechaFin;
}
