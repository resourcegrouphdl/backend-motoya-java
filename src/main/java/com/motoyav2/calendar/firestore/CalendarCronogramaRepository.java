package com.motoyav2.calendar.firestore;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio reactivo Firestore para documentos de cronogramas de calendario.
 * La colección se define en {@link CalendarCronogramaDocument} (@Document).
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Repository
public interface CalendarCronogramaRepository
        extends FirestoreReactiveRepository<CalendarCronogramaDocument> {
}
