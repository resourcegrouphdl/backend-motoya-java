package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.domain.port.out.AlertaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class AlertaRepositoryAdapter implements AlertaRepository {

    private static final String COL = "alertas";
    private final Firestore db;

    @Override
    public Mono<Void> save(Map<String, Object> alerta) {
        return toMono(db.collection(COL).add(alerta)).then();
    }
}
