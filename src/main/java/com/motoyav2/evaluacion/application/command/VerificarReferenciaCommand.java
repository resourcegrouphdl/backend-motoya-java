package com.motoyav2.evaluacion.application.command;

public record VerificarReferenciaCommand(
        String referenciaId,
        String estadoVerificacion,
        String resultadoContacto,
        Integer scoreVerificacion,
        String observaciones,
        String actitudDuranteContacto,
        String evaluadorId
) {}
