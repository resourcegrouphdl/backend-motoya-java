package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.port.out.VehiculoRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.FirestoreCollections;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.VehiculoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class VehiculoRepositoryAdapter implements VehiculoRepository {

    private static final String COL = FirestoreCollections.VEHICULOS;
    private final Firestore db;

    @Override
    public Mono<Vehiculo> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(VehiculoMapper::toDomain);
    }

    @Override
    public Mono<String> create(Map<String, Object> fields) {
        return toMono(db.collection(COL).add(fields))
                .map(ref -> ref.getId());
    }
}
