package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.domain.port.out.PersonaRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.FirestoreCollections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toFlux;
import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

/**
 * Adaptador para la colección `personas`.
 * El documento se identifica por documentNumber (campo único, usado como clave de búsqueda).
 * Se usa upsert: si ya existe, solo actualiza los campos de contacto (no crea duplicados).
 */
@Component
@RequiredArgsConstructor
public class PersonaRepositoryAdapter implements PersonaRepository {

    private static final String COL = FirestoreCollections.PERSONAS;
    private final Firestore db;

    @Override
    public Mono<Void> upsert(String documentNumber, Map<String, Object> fields) {
        // Busca el doc existente; si no existe, crea uno nuevo; si existe, actualiza
        return toFlux(db.collection(COL)
                .whereEqualTo("documentNumber", documentNumber)
                .limit(1).get())
                .next()
                .flatMap(existing -> toMono(db.collection(COL).document(existing.getId()).update(fields)).then())
                .switchIfEmpty(
                        toMono(db.collection(COL).add(fields)).then()
                );
    }

    @Override
    public Mono<Map<String, Object>> findByDocumentNumber(String documentNumber) {
        return toFlux(db.collection(COL)
                .whereEqualTo("documentNumber", documentNumber)
                .limit(1).get())
                .next()
                .map(doc -> {
                    Map<String, Object> data = doc.getData();
                    if (data != null) data.put("_id", doc.getId());
                    return data;
                });
    }
}
