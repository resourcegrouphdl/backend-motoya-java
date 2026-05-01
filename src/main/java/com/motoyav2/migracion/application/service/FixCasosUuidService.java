package com.motoyav2.migracion.application.service;

import com.google.cloud.firestore.*;
import com.motoyav2.migracion.application.dto.FixCasosUuidResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Corrige documentos en cobranzas-casos cuyo document ID es un UUID auto-generado
 * en lugar del contratoId. Esto ocurre cuando se guardaron antes de que se añadiera
 * @DocumentId al campo contratoId de CasoCobranzaDocument.
 *
 * Para cada doc con ID=UUID y field contratoId presente:
 *   1. Verifica que no exista ya un doc con ese contratoId como ID.
 *   2. Crea nuevo doc en cobranzas-casos/{contratoId} sin el field contratoId.
 *   3. Borra el doc UUID original.
 */
@Slf4j
@Service
public class FixCasosUuidService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                    Pattern.CASE_INSENSITIVE);

    @Autowired(required = false)
    private Firestore adminFirestore;

    public Mono<FixCasosUuidResponse> ejecutar() {
        if (adminFirestore == null) {
            return Mono.error(new IllegalStateException(
                    "Firebase Admin SDK no disponible. Verificar inicialización de Firebase."));
        }
        return Mono.fromCallable(this::doFix)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private FixCasosUuidResponse doFix() throws Exception {
        List<String> detalle = new ArrayList<>();
        int corregidos = 0, conflictos = 0, sinContratoId = 0, errores = 0;

        QuerySnapshot snap = adminFirestore.collection("cobranzas-casos").get().get();
        int totalRevisados = snap.size();
        log.info("[FixCasosUuid] Total documentos en cobranzas-casos: {}", totalRevisados);

        for (QueryDocumentSnapshot doc : snap.getDocuments()) {
            String docId = doc.getId();

            if (!UUID_PATTERN.matcher(docId).matches()) {
                continue; // ya tiene formato correcto
            }

            Map<String, Object> data = doc.getData();
            String contratoId = (String) data.get("contratoId");

            if (contratoId == null || contratoId.isBlank()) {
                log.warn("[FixCasosUuid] Doc {} no tiene field contratoId — omitido", docId);
                detalle.add("SIN_CONTRATO_ID: " + docId);
                sinContratoId++;
                continue;
            }

            // Verificar que no exista ya un doc con el contratoId como ID
            DocumentSnapshot existente = adminFirestore
                    .collection("cobranzas-casos")
                    .document(contratoId)
                    .get().get();

            if (existente.exists()) {
                log.warn("[FixCasosUuid] Ya existe doc con ID={} — omitiendo UUID {}", contratoId, docId);
                detalle.add("CONFLICTO: uuid=" + docId + " → contratoId=" + contratoId + " ya existe");
                conflictos++;
                continue;
            }

            try {
                // Copia todos los fields excepto contratoId (es el @DocumentId, no debe guardarse)
                Map<String, Object> nuevoData = new java.util.LinkedHashMap<>(data);
                nuevoData.remove("contratoId");

                WriteBatch batch = adminFirestore.batch();
                batch.set(adminFirestore.collection("cobranzas-casos").document(contratoId), nuevoData);
                batch.delete(adminFirestore.collection("cobranzas-casos").document(docId));
                batch.commit().get();

                log.info("[FixCasosUuid] Corregido: {} → {}", docId, contratoId);
                detalle.add("OK: uuid=" + docId + " → contratoId=" + contratoId);
                corregidos++;

            } catch (Exception e) {
                log.error("[FixCasosUuid] Error procesando doc {}: {}", docId, e.getMessage());
                detalle.add("ERROR: uuid=" + docId + " → " + e.getMessage());
                errores++;
            }
        }

        log.info("[FixCasosUuid] Resultado — revisados={}, corregidos={}, conflictos={}, sinContratoId={}, errores={}",
                totalRevisados, corregidos, conflictos, sinContratoId, errores);

        return new FixCasosUuidResponse(totalRevisados, corregidos, conflictos, sinContratoId, errores, detalle);
    }
}
