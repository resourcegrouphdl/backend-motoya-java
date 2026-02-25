package com.motoyav2.calendar.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Documento Firestore que representa un cronograma de cuotas y sus eventos de calendario.
 * Colección: calendar_cronogramas
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "calendar_cronogramas")
public class CalendarCronogramaDocument {

    /** ID auto-generado por Firestore */
    @DocumentId
    private String id;

    private String nombreCliente;
    private String descripcion;

    /** Estado del cronograma: PENDIENTE, PAGADO, ATRASADO */
    private String estado;

    /** Total de cuotas solicitadas (incluyendo las que fallaron) */
    private int totalCuotas;

    private Timestamp createdAt;

    /** Solo contiene los eventos que se crearon exitosamente */
    private List<EventoInfo> eventos;

    /**
     * Información de cada evento de Google Calendar creado exitosamente.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventoInfo {

        /** Número de cuota */
        private int numero;

        /** Fecha en formato yyyy-MM-dd */
        private String fecha;

        /** ID del evento en Google Calendar */
        private String eventId;

        /** ID del calendario donde se creó el evento */
        private String calendarId;
    }
}
