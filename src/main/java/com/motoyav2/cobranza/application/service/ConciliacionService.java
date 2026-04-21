package com.motoyav2.cobranza.application.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.motoyav2.cobranza.application.dto.ConciliacionDto;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Conciliación entre la colección {@code contratos} y {@code cobranzas-casos}.
 *
 * Un contrato en estado FIRMADO o ACTIVO debería tener un caso de cobranza.
 * Si no lo tiene, aparece en el resultado para que el equipo pueda iniciar el caso manualmente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliacionService {

    private static final List<String> ESTADOS_CON_COBRANZA = List.of("FIRMADO", "ACTIVO");

    private final CasoCobranzaPort casoPort;
    private final Firestore        firestore;

    public Mono<ConciliacionDto> conciliar() {
        // 1. Recopilar todos los contratoId existentes en cobranzas-casos (reactive)
        Mono<Set<String>> casosIdsMono = casoPort.findAll()
                .map(c -> c.getContratoId())
                .collect(Collectors.toSet());

        // 2. Consultar contratos firmados/activos (blocking SDK → boundedElastic)
        return casosIdsMono.flatMap(casosIds ->
                Mono.fromCallable(() -> queryContratosFirmados(casosIds))
                        .subscribeOn(Schedulers.boundedElastic())
        );
    }

    // ── Consulta síncrona al SDK de Firestore ─────────────────────────────────

    private ConciliacionDto queryContratosFirmados(Set<String> casosIds) {
        try {
            List<QueryDocumentSnapshot> docs = firestore
                    .collection("contratos")
                    .whereIn("estado", ESTADOS_CON_COBRANZA)
                    .get().get()
                    .getDocuments();

            List<ConciliacionDto.ContratoSinCasoItem> sinCaso = docs.stream()
                    .filter(doc -> !casosIds.contains(doc.getId()))
                    .map(this::toItem)
                    .collect(Collectors.toList());

            log.info("[CONCILIACION] contratos={} casos={} sinCaso={}",
                    docs.size(), casosIds.size(), sinCaso.size());

            return new ConciliacionDto(
                    docs.size(),
                    casosIds.size(),
                    sinCaso.size(),
                    sinCaso
            );

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("[CONCILIACION] Error consultando contratos: {}", e.getMessage());
            return new ConciliacionDto(0, casosIds.size(), 0, List.of());
        }
    }

    private ConciliacionDto.ContratoSinCasoItem toItem(QueryDocumentSnapshot doc) {
        String clienteNombre   = "";
        String clienteTelefono = "";
        String clienteDni      = "";

        Object titularRaw = doc.get("titular");
        if (titularRaw instanceof java.util.Map<?, ?> titular) {
            clienteNombre   = strOf(titular, "nombres") + " " + strOf(titular, "apellidos");
            clienteTelefono = strOf(titular, "telefono");
            clienteDni      = strOf(titular, "numeroDocumento");
        }

        String tiendaNombre = "";
        Object tiendaRaw = doc.get("tienda");
        if (tiendaRaw instanceof java.util.Map<?, ?> tienda) {
            tiendaNombre = strOf(tienda, "nombreTienda");
        }

        String fechaCreacion = "";
        Object ts = doc.get("fechaCreacion");
        if (ts != null) fechaCreacion = ts.toString();

        return new ConciliacionDto.ContratoSinCasoItem(
                doc.getId(),
                strOf(doc.getData(), "numeroContrato"),
                strOf(doc.getData(), "estado"),
                clienteNombre.trim(),
                clienteTelefono,
                clienteDni,
                tiendaNombre,
                fechaCreacion
        );
    }

    private String strOf(java.util.Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
