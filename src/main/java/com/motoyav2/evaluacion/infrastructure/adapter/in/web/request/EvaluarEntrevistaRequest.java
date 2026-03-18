package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import java.util.List;

public record EvaluarEntrevistaRequest(
        String solicitudId,
        String modalidad,           // videollamada | presencial | telefonica
        String plataforma,
        String puntualidad,         // puntual | retraso_leve | retraso_significativo | no_asistio
        Integer presentacionPersonal,
        Integer actitudColaboracion,
        Integer coherenciaRespuestas,
        Integer nivelConfianza,
        Integer scoreEntrevista,
        String observacionesCliente,
        String observacionesFiador,
        String observacionesDomicilio,
        String observacionesCapacidadPago,
        List<String> hallazgosPositivos,
        List<String> hallazgosNegativos,
        String recomendacion,       // aprobar | rechazar | condicional | requiere_comite | revisar
        String motivoRecomendacion,
        List<String> condiciones,
        Boolean esBorrador
) {}
