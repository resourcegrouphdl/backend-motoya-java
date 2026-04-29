package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.Timestamp;
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

import java.time.Instant;
import java.util.List;
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

    /** Máximo de documentos a cargar desde Firestore cuando hay búsqueda de texto. */
    private static final int SEARCH_FETCH_LIMIT = 300;

    @Override
    public Flux<Solicitud> findAll(String estado, String prioridad, String search, String tiendaId, int limit, int offset) {
        var query = (Query) db.collection(COL)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (estado != null && !estado.isBlank()) {
            query = query.whereEqualTo("estado", estado);
        }
        if (prioridad != null && !prioridad.isBlank()) {
            query = query.whereEqualTo("prioridad", prioridad);
        }
        if (tiendaId != null && !tiendaId.isBlank()) {
            query = query.whereEqualTo("vendedor.tienda", tiendaId);
        }

        // Con búsqueda de texto: traer lote amplio desde el inicio, filtrar en memoria y paginar.
        // Sin búsqueda: paginación directa en Firestore (más eficiente).
        if (search != null && !search.isBlank()) {
            return toFlux(query.limit(SEARCH_FETCH_LIMIT).get())
                    .mapNotNull(SolicitudMapper::toDomain)
                    .filter(s -> matchesSearch(s, search))
                    .skip(offset)
                    .take(limit);
        }

        return toFlux(query.offset(offset).limit(limit).get())
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Mono<Long> countAll(String estado, String prioridad, String search, String tiendaId) {
        var query = (Query) db.collection(COL);
        if (estado != null && !estado.isBlank()) {
            query = query.whereEqualTo("estado", estado);
        }
        if (prioridad != null && !prioridad.isBlank()) {
            query = query.whereEqualTo("prioridad", prioridad);
        }
        if (tiendaId != null && !tiendaId.isBlank()) {
            query = query.whereEqualTo("vendedor.tienda", tiendaId);
        }

        // Con búsqueda de texto: contar sobre el mismo lote filtrado en memoria.
        // Sin búsqueda: agregación nativa de Firestore (más eficiente).
        if (search != null && !search.isBlank()) {
            return toFlux(query.limit(SEARCH_FETCH_LIMIT).get())
                    .mapNotNull(SolicitudMapper::toDomain)
                    .filter(s -> matchesSearch(s, search))
                    .count();
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
    public Flux<Solicitud> findByTitularDni(String titularDni, int limit) {
        return toFlux(db.collection(COL)
                .whereEqualTo("titularDni", titularDni)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit).get())
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Flux<Solicitud> findByFiadorDni(String fiadorDni, int limit) {
        return toFlux(db.collection(COL)
                .whereEqualTo("fiadorDni", fiadorDni)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit).get())
                .mapNotNull(SolicitudMapper::toDomain);
    }

    @Override
    public Mono<Solicitud> findActivaByTitularTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return Mono.empty();
        String normalized = normalizePhone(telefono);
        return toFlux(db.collection(COL)
                        .whereEqualTo("titularTelefono", normalized)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(5).get())
                .mapNotNull(SolicitudMapper::toDomain)
                .filter(this::esActiva)
                .next();
    }

    @Override
    public Mono<Solicitud> findActivaByFiadorTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) return Mono.empty();
        String normalized = normalizePhone(telefono);
        return toFlux(db.collection(COL)
                        .whereEqualTo("fiadorTelefono", normalized)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(5).get())
                .mapNotNull(SolicitudMapper::toDomain)
                .filter(this::esActiva)
                .next();
    }

    @Override
    public Flux<Solicitud> findAbandonadas(int diasInactividad) {
        Timestamp cutoff = Timestamp.ofTimeSecondsAndNanos(
                Instant.now().minusSeconds((long) diasInactividad * 86_400).getEpochSecond(), 0);

        List<String> estadosAbandonables = List.of("pendiente", "en_revision_inicial", "evaluacion_documental");

        return Flux.fromIterable(estadosAbandonables)
                .flatMap(estado -> toFlux(db.collection(COL)
                        .whereEqualTo("estado", estado)
                        .whereLessThan("updatedAt", cutoff)
                        .limit(50)
                        .get())
                        .mapNotNull(SolicitudMapper::toDomain));
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

    @Override
    public Mono<Void> delete(String id) {
        return toMono(db.collection(COL).document(id).delete()).then();
    }

    private boolean esActiva(Solicitud s) {
        if (s.getEstado() == null) return true;
        return switch (s.getEstado()) {
            case CANCELADO, RECHAZADO, ARCHIVADA, ENTREGA_COMPLETADA -> false;
            default -> true;
        };
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) digits = digits.substring(2);
        if (digits.length() == 9) return "+51" + digits;
        return digits;
    }

    private boolean matchesSearch(Solicitud s, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase();
        return (s.getNumeroSolicitud()        != null && s.getNumeroSolicitud().toLowerCase().contains(q))
                || (s.getCodigoDeSolicitud()  != null && s.getCodigoDeSolicitud().toLowerCase().contains(q))
                || (s.getTitularNombreCompleto() != null && s.getTitularNombreCompleto().toLowerCase().contains(q))
                || (s.getTitularDni()         != null && s.getTitularDni().contains(q))
                || (s.getVendedorNombre()     != null && s.getVendedorNombre().toLowerCase().contains(q));
    }
}
