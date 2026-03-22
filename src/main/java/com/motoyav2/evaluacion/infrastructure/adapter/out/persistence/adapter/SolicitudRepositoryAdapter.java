package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.SolicitudMapper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.FirestoreCollections;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toFlux;
import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class SolicitudRepositoryAdapter implements SolicitudRepository {

    private static final String COL = FirestoreCollections.SOLICITUDES;
    private final Firestore db;

    @Override
    public Mono<Solicitud> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Mono<Solicitud> findByNumeroSolicitud(String numeroSolicitud) {
        return toFlux(db.collection(COL).whereEqualTo("numeroSolicitud", numeroSolicitud).limit(1).get())
                .next()
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Flux<Solicitud> findByEstado(String estado, int limit, int offset) {
        return toFlux(db.collection(COL)
                .whereEqualTo("estado", estado)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .offset(offset).limit(limit).get())
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Flux<Solicitud> findAll(String estado, String prioridad, String search, int limit, int offset) {
        var query = (Query) db.collection(COL)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (estado != null && !estado.isBlank()) {
            query = query.whereEqualTo("estado", estado);
        }
        if (prioridad != null && !prioridad.isBlank()) {
            query = query.whereEqualTo("prioridad", prioridad);
        }

        return toFlux(query.offset(offset).limit(limit).get())
                .mapNotNull(SolicitudMapper::toDomain)
                .filter(s -> matchesSearch(s, search));
    }

    @Override
    public Mono<Long> countAll(String estado, String prioridad, String search) {
        var query = (Query) db.collection(COL);
        if (estado != null && !estado.isBlank()) {
            query = query.whereEqualTo("estado", estado);
        }
        if (prioridad != null && !prioridad.isBlank()) {
            query = query.whereEqualTo("prioridad", prioridad);
        }
        return toMono(query.count().get()).map(agg -> agg.getCount());
    }

    @Override
    public Flux<Solicitud> findByVendedorId(String vendedorId, int limit, int offset) {
        return toFlux(db.collection(COL)
                .whereEqualTo("vendedorId", vendedorId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .offset(offset).limit(limit).get())
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Mono<Long> countByVendedorId(String vendedorId) {
        return toMono(db.collection(COL)
                .whereEqualTo("vendedorId", vendedorId)
                .count().get())
                .map(agg -> agg.getCount());
    }

    @Override
    public Mono<String> create(Map<String, Object> fields) {
        return toMono(db.collection(COL).add(fields))
                .map(ref -> ref.getId());
    }

    @Override
    public Mono<Void> updateFields(String id, Map<String, Object> fields) {
        return toMono(db.collection(COL).document(id).update(fields)).then();
    }

    private boolean matchesSearch(Solicitud s, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase();
        return (s.getNumeroSolicitud() != null && s.getNumeroSolicitud().toLowerCase().contains(q))
                || (s.getVendedorNombre() != null && s.getVendedorNombre().toLowerCase().contains(q));
    }
}
