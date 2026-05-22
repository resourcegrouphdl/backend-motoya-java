package com.motoyav2.debug;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuscarClienteService {

    private final Firestore firestore;

    /**
     * Busca clientes cuyo telefono1 coincida con el número ingresado.
     * Normaliza el input para cubrir ambos formatos: 957311203 y +51957311203.
     */
    public Flux<Map<String, Object>> buscarPorTelefono(String telefono) {
        Set<String> variantes = normalizarVariantes(telefono.trim());
        log.info("[BUSCAR-CLIENTE] Buscando variantes={}", variantes);

        return Flux.fromIterable(variantes)
                .flatMap(variante -> Mono.fromCallable(() ->
                        firestore.collection("clientes_v1")
                                .whereEqualTo("telefono1", variante)
                                .limit(20)
                                .get().get()
                                .getDocuments()
                ).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable))
                .distinct(QueryDocumentSnapshot::getId)
                .map(doc -> {
                    Map<String, Object> data = new HashMap<>(doc.getData());
                    data.put("id", doc.getId());
                    return data;
                });
    }

    /** Devuelve ambas variantes: con y sin prefijo +51 / 51. */
    private Set<String> normalizarVariantes(String input) {
        Set<String> variantes = new LinkedHashSet<>();

        // Quitar cualquier prefijo internacional peruano
        String sinPrefijo = input;
        if (sinPrefijo.startsWith("+51")) {
            sinPrefijo = sinPrefijo.substring(3);
        } else if (sinPrefijo.startsWith("51") && sinPrefijo.length() > 9) {
            sinPrefijo = sinPrefijo.substring(2);
        }

        variantes.add(sinPrefijo);           // ej. 957311203
        variantes.add("+51" + sinPrefijo);   // ej. +51957311203
        return variantes;
    }
}