package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import com.motoyav2.evaluacion.domain.port.out.HistorialEstadoRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.HistorialEstadoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toFlux;
import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class HistorialEstadoRepositoryAdapter implements HistorialEstadoRepository {

    private static final String COL = "cambios_estado_solicitud";
    private final Firestore db;

    @Override
    public Mono<Void> save(HistorialEstado historial) {
        return toMono(db.collection(COL).add(HistorialEstadoMapper.toFirestore(historial))).then();
    }

    @Override
    public Flux<HistorialEstado> findBySolicitudId(String solicitudId) {
        return toFlux(db.collection(COL)
                .whereEqualTo("solicitudId", solicitudId)
                .orderBy("fechaCambio", Query.Direction.DESCENDING)
                .get())
                .mapNotNull(HistorialEstadoMapper::toDomain);
    }
}
