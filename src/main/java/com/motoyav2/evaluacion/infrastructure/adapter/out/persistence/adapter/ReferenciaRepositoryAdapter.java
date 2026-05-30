package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.domain.model.Referencia;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.FirestoreCollections;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.ReferenciaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toFlux;
import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Component
@RequiredArgsConstructor
public class ReferenciaRepositoryAdapter implements ReferenciaRepository {

    private static final String COL = FirestoreCollections.REFERENCIAS;
    private final Firestore db;

    @Override
    public Mono<Referencia> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(ReferenciaMapper::toDomain);
    }

    @Override
    public Flux<Referencia> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Flux.empty();
        return Flux.fromIterable(ids)
                .flatMap(id -> toMono(db.collection(COL).document(id).get())
                        .mapNotNull(ReferenciaMapper::toDomain), 5);
    }

    @Override
    public Mono<Referencia> findByTelefonoAndEstadoWaEnviado(String telefono) {
        // Requiere índice compuesto en Firestore: (telefono ASC, estadoVerificacion ASC)
        return toFlux(db.collection(COL)
                        .whereEqualTo("telefono", normalizePhone(telefono))
                        .whereEqualTo("estadoVerificacion", "wa_enviado")
                        .limit(1)
                        .get())
                .next()
                .mapNotNull(ReferenciaMapper::toDomain);
    }

    @Override
    public Flux<Referencia> findByTelefono(String telefono, int limit) {
        if (telefono == null || telefono.isBlank()) return Flux.empty();
        return toFlux(db.collection(COL)
                        .whereEqualTo("telefono", normalizePhone(telefono))
                        .limit(limit).get())
                .mapNotNull(ReferenciaMapper::toDomain);
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

    /** Normaliza el número al mismo formato que usa FactilizaWhatsAppNotificationAdapter. */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        return digits;
    }
}
