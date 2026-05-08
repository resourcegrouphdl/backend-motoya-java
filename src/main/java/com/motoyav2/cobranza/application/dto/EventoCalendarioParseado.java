package com.motoyav2.cobranza.application.dto;

import java.time.LocalDate;

/**
 * Un evento de Google Calendar ya parseado.
 * nombreCompleto: "VALDEZ MOTA RAFAEL DANIEL"
 * numeroCuota:   8
 * monto:         339.00
 * fechaVencimiento: 2026-02-23
 * pagada: true si el colorId del evento indica cuota pagada (mismo criterio que migración)
 */
public record EventoCalendarioParseado(
        String nombreCompleto,
        int numeroCuota,
        double monto,
        LocalDate fechaVencimiento,
        String tituloOriginal,
        boolean pagada
) {}
