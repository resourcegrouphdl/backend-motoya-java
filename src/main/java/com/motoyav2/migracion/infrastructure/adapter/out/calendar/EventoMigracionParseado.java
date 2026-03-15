package com.motoyav2.migracion.infrastructure.adapter.out.calendar;

import java.time.LocalDate;

/**
 * Un evento de Google Calendar parseado para el módulo de migración.
 */
public record EventoMigracionParseado(
        String nombreCompleto,
        int numeroCuota,
        double monto,
        LocalDate fechaVencimiento,
        String tituloOriginal,
        /** true si el colorId del evento indica cuota pagada */
        boolean pagada
) {}
