package com.motoyav2.calendar.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Respuesta del endpoint POST /api/calendar/cronograma.
 * Incluye resumen de eventos creados y lista de errores si los hubo.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Data
@Builder
public class CronogramaResponse {

    /** Total de cuotas enviadas en el request */
    private int totalSolicitado;

    /** Cantidad de eventos creados exitosamente en Google Calendar */
    private int eventosCreados;

    /** Lista de cuotas que fallaron (vacía si todo fue exitoso) */
    private List<EventoError> errores;

    /** Detalle de un evento que falló al crearse */
    @Data
    @Builder
    public static class EventoError {
        private int numeroCuota;
        private String fecha;
        private String mensaje;
    }
}
