package com.motoyav2.riesgointerno.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toFlux;
import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class FirestoreRegistroRiesgoAdapter implements RegistroRiesgoRepository {

    private static final String COL = "riesgo_registros";
    private static final int SEARCH_FETCH_LIMIT = 200;

    private final Firestore db;

    @Override
    public Mono<RegistroRiesgo> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(RegistroRiesgoMapper::toDomain);
    }

    @Override
    public Flux<RegistroRiesgo> findAll(String nivelRiesgo, String estadoRegistro, String search, int limit, int offset) {
        var query = (Query) db.collection(COL)
                .orderBy("fechaRegistro", Query.Direction.DESCENDING);

        if (nivelRiesgo != null && !nivelRiesgo.isBlank()) {
            query = query.whereEqualTo("nivelRiesgo", nivelRiesgo.toUpperCase());
        }
        if (estadoRegistro != null && !estadoRegistro.isBlank()) {
            query = query.whereEqualTo("estadoRegistro", estadoRegistro.toUpperCase());
        }

        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            return toFlux(query.limit(SEARCH_FETCH_LIMIT).get())
                    .mapNotNull(RegistroRiesgoMapper::toDomain)
                    .filter(r -> matchesSearch(r, q))
                    .skip(offset)
                    .take(limit);
        }

        return toFlux(query.offset(offset).limit(limit).get())
                .mapNotNull(RegistroRiesgoMapper::toDomain);
    }

    @Override
    public Mono<Long> count(String nivelRiesgo, String estadoRegistro) {
        var query = (Query) db.collection(COL);
        if (nivelRiesgo != null && !nivelRiesgo.isBlank()) {
            query = query.whereEqualTo("nivelRiesgo", nivelRiesgo.toUpperCase());
        }
        if (estadoRegistro != null && !estadoRegistro.isBlank()) {
            query = query.whereEqualTo("estadoRegistro", estadoRegistro.toUpperCase());
        }
        return toFlux(query.limit(SEARCH_FETCH_LIMIT).get()).count();
    }

    @Override
    public Flux<RegistroRiesgo> findByTelefono(String telefono) {
        return toFlux(db.collection(COL)
                .whereArrayContains("telefonos", normalizar(telefono))
                .orderBy("fechaRegistro", Query.Direction.DESCENDING)
                .limit(20).get())
                .mapNotNull(RegistroRiesgoMapper::toDomain);
    }

    @Override
    public Flux<RegistroRiesgo> findByDni(String dni) {
        return toFlux(db.collection(COL)
                .whereEqualTo("dniRegistrado", dni)
                .orderBy("fechaRegistro", Query.Direction.DESCENDING)
                .limit(20).get())
                .mapNotNull(RegistroRiesgoMapper::toDomain);
    }

    @Override
    public Mono<RegistroRiesgo> create(RegistroRiesgo registro) {
        String id = UUID.randomUUID().toString();
        return Mono.defer(() -> {
            Map<String, Object> data = RegistroRiesgoMapper.toFirestore(registro);
            return toMono(db.collection(COL).document(id).set(data));
        }).map(wr -> registro.toBuilder().id(id).build());
    }

    @Override
    public Mono<Void> updateFields(String id, Map<String, Object> fields) {
        return toMono(db.collection(COL).document(id).update(fields))
                .then();
    }

    @Override
    public Mono<Void> delete(String id) {
        return toMono(db.collection(COL).document(id).delete()).then();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String normalizar(String tel) {
        if (tel == null) return "";
        String d = tel.replaceAll("[^0-9]", "");
        if (d.startsWith("51") && d.length() == 11) d = d.substring(2);
        return d;
    }

    private boolean matchesSearch(RegistroRiesgo r, String q) {
        if (r.getNombreRegistrado() != null && r.getNombreRegistrado().toLowerCase().contains(q)) return true;
        if (r.getDniRegistrado() != null && r.getDniRegistrado().contains(q)) return true;
        if (r.getTelefonos() != null && r.getTelefonos().stream().anyMatch(t -> t.contains(q))) return true;
        return false;
    }
}
