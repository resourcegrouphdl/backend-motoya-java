package com.motoyav2.evaluacion.application.command;

import java.util.List;

public record EvaluarEntrevistaCommand(
        String clienteId,
        String solicitudId,
        String modalidad,
        String plataforma,
        String puntualidad,
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
        String recomendacion,
        String motivoRecomendacion,
        List<String> condiciones,
        Boolean esBorrador,
        String usuarioId,
        String usuarioNombre
) {}
