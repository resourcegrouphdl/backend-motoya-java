package com.motoyav2.migracion.domain.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una cuota detectada en Google Calendar, embebida en MigracionStagingDocument.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CuotaStagingDocument {

    /** Número de cuota (1, 2, 3...) */
    private Integer cuota;
    /** YYYY-MM-DD — fecha del evento en Calendar */
    private String fechaVencimiento;
    /** true si el colorId indica cuota pagada */
    private Boolean pagada;
    /** Título original del evento para auditoría */
    private String tituloOriginal;
}
