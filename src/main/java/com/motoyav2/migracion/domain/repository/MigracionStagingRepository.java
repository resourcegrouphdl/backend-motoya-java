package com.motoyav2.migracion.domain.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.migracion.domain.document.MigracionStagingDocument;
import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo para la colección migracion-staging.
 * Escaneado automáticamente por @EnableReactiveFirestoreRepositories(basePackages = "com.motoyav2").
 *
 * Nota Firestore: findByClienteNombreCalendarAndFechaInicio requiere índice compuesto en:
 *   clienteNombreCalendar ASC + fechaInicio ASC
 */
public interface MigracionStagingRepository extends FirestoreReactiveRepository<MigracionStagingDocument> {

    Flux<MigracionStagingDocument> findByEstado(String estado);

    Flux<MigracionStagingDocument> findByClienteNombreCalendarAndFechaInicio(
            String clienteNombreCalendar, String fechaInicio);
}
