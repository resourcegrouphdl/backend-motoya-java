package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.ClienteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class ClienteRepositoryAdapter implements ClienteRepository {

    private static final String COL = "clientes_v1";
    private final Firestore db;

    @Override
    public Mono<Cliente> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(ClienteMapper::toDomain);
    }

    @Override
    public Mono<Void> updateFields(String id, Map<String, Object> fields) {
        return toMono(db.collection(COL).document(id).update(fields)).then();
    }
}
