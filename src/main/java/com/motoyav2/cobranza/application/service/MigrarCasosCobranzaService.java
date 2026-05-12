package com.motoyav2.cobranza.application.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosFiadorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.CasoCobranzaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Corrige documentos de "cobranzas-casos" que tienen el campo "contratoId"
 * almacenado en el body del documento, lo cual conflictúa con @DocumentId.
 *
 * Solución segura: eliminar SOLO el campo "contratoId" del body usando
 * FieldValue.delete() — sin mover ni borrar ningún documento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrarCasosCobranzaService {

    private static final String COLECCION       = "cobranzas-casos";
    private static final String SUB_EVENTOS     = "cobranzas-eventos";
    private static final String SUB_MOVIMIENTOS = "cobranzas-movimientos";
    private static final String SUB_PROMESAS    = "cobranzas-promesas";

    // Los 3 UUIDs que fueron borrados por error y necesitan reconstruirse
    private static final List<String> UUIDS_BORRADOS = List.of(
            "2216c1f3-f89b-4ade-a0e3-440bfcd2eee0",
            "3a1dbd94-a30a-45f1-9144-d5afed2aea18",
            "5057a36f-8c1a-42ba-a33e-fff9c9351320"
    );

    private final Firestore firestore;
    private final CasoCobranzaRepository repository;

    // -------------------------------------------------------------------------
    // CORRECCIÓN SEGURA: elimina el campo "contratoId" del body de todos los
    // documentos que lo tengan, sin borrar ni mover ningún documento.
    // -------------------------------------------------------------------------

    public Mono<List<String>> limpiarCampoContratoId() {
        return Mono.fromCallable(this::ejecutarLimpieza)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<String> ejecutarLimpieza() throws Exception {
        QuerySnapshot snapshot = firestore.collection(COLECCION).get().get();

        List<String> corregidos = new ArrayList<>();
        int yaCorrectos = 0;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;

            if (!data.containsKey("contratoId")) {
                yaCorrectos++;
                continue;
            }

            // Solo elimina el campo "contratoId" del body — el documento queda intacto
            firestore.collection(COLECCION)
                    .document(doc.getId())
                    .update("contratoId", FieldValue.delete())
                    .get();

            log.info("[Limpieza] Campo 'contratoId' eliminado del body del doc={}", doc.getId());
            corregidos.add(doc.getId());
        }

        log.info("[Limpieza] Completado: {} corregidos, {} ya estaban correctos", corregidos.size(), yaCorrectos);
        return corregidos;
    }

    // -------------------------------------------------------------------------
    // Reconstrucción automática de los 3 casos borrados desde contratos
    // -------------------------------------------------------------------------

    public Mono<List<String>> autoReconstruir() {
        return Mono.fromCallable(this::ejecutarReconstruccion)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<String> ejecutarReconstruccion() throws Exception {
        List<String> resultado = new ArrayList<>();

        for (String uuid : UUIDS_BORRADOS) {
            DocumentSnapshot contratoSnap = firestore.collection("contratos").document(uuid).get().get();
            if (!contratoSnap.exists()) {
                log.warn("[Reconstruccion] No se encontró contrato para uuid={}", uuid);
                resultado.add("SIN CONTRATO: " + uuid);
                continue;
            }

            Map<String, Object> c = contratoSnap.getData();
            CasoCobranzaDocument doc = mapearContrato(uuid, c);

            boolean yaExiste = repository.findById(uuid).blockOptional().isPresent();
            if (yaExiste) {
                log.warn("[Reconstruccion] Ya existe caso con id={}, omitiendo", uuid);
                resultado.add("YA EXISTE: " + uuid);
                continue;
            }

            repository.save(doc).block();
            log.info("[Reconstruccion] OK: caso reconstruido id={} cliente={}", uuid, doc.getClienteNombre());
            resultado.add("OK: " + uuid + " → " + doc.getClienteNombre());
        }

        return resultado;
    }

    private CasoCobranzaDocument mapearContrato(String uuid, Map<String, Object> c) {
        Map<String, Object> titular = asMap(c.get("titular"));
        Map<String, Object> fiador  = asMap(c.get("fiador"));
        Map<String, Object> df      = asMap(c.get("datosFinancieros"));
        Map<String, Object> tienda  = asMap(c.get("tienda"));

        DatosTitularDocument titularDoc = DatosTitularDocument.builder()
                .nombres(str(titular, "nombres"))
                .apellidos(str(titular, "apellidos"))
                .tipoDocumento(str(titular, "tipoDocumento"))
                .numeroDocumento(str(titular, "numeroDocumento"))
                .telefono(str(titular, "telefono"))
                .email(str(titular, "email"))
                .direccion(str(titular, "direccion"))
                .distrito(str(titular, "distrito"))
                .provincia(str(titular, "provincia"))
                .departamento(str(titular, "departamento"))
                .build();

        DatosFiadorDocument fiadorDoc = fiador != null ? DatosFiadorDocument.builder()
                .nombres(str(fiador, "nombres"))
                .apellidos(str(fiador, "apellidos"))
                .tipoDocumento(str(fiador, "tipoDocumento"))
                .numeroDocumento(str(fiador, "numeroDocumento"))
                .telefono(str(fiador, "telefono"))
                .email(str(fiador, "email"))
                .parentesco(str(fiador, "parentesco"))
                .build() : null;

        List<CuotaCronogramaDocument> cronograma = mapearCuotas(c);
        int cuotasTotales  = cronograma.size();
        int cuotasPagadas  = (int) cronograma.stream().filter(q -> "PAGADA".equals(q.getEstado())).count();
        double totalPagado = cronograma.stream()
                .filter(q -> "PAGADA".equals(q.getEstado()))
                .mapToDouble(q -> q.getMonto() != null ? q.getMonto() : 0.0).sum();

        double montoFinanciado = df != null ? toDouble(df.get("montoFinanciado")) : 0.0;

        String fechaPrimerCuotaImpaga = cronograma.stream()
                .filter(q -> !"PAGADA".equals(q.getEstado()))
                .map(CuotaCronogramaDocument::getFechaVencimiento)
                .findFirst().orElse(null);

        Map<String, Object> cpi = asMap(c.get("contratoParaImprimir"));
        String motoDesc = (cpi != null)
                ? (str(cpi, "marcaDeMoto") + " " + str(cpi, "modelo")).trim()
                : null;

        String storeId = tienda != null ? str(tienda, "tiendaId") : null;
        Date ahora = new Date();

        return CasoCobranzaDocument.builder()
                .contratoId(uuid)
                .storeId(storeId)
                .titular(titularDoc)
                .fiador(fiadorDoc)
                .clienteNombre(titularDoc.nombreCompleto())
                .clienteTelefono(titularDoc.getTelefono())
                .clienteDni(titularDoc.getNumeroDocumento())
                .motoDescripcion(motoDesc)
                .capitalOriginal(montoFinanciado)
                .saldoActual(montoFinanciado - totalPagado)
                .nivelEstrategia("AL_DIA")
                .estadoCaso("EN_SEGUIMIENTO")
                .cicloVida("ACTIVO")
                .cronograma(cronograma)
                .numeroCuotasTotales(cuotasTotales)
                .numeroCuotasPagadas(cuotasPagadas)
                .totalPagado(totalPagado)
                .totalMora(0.0)
                .totalCondonado(0.0)
                .mensajesNoLeidos(0)
                .fechaVencimientoPrimerCuotaImpaga(fechaPrimerCuotaImpaga)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA_RECUPERACION")
                .actualizadoPor("SISTEMA_RECUPERACION")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<CuotaCronogramaDocument> mapearCuotas(Map<String, Object> contrato) {
        Object raw = contrato.get("cuotas");
        if (!(raw instanceof List<?> lista)) return List.of();
        List<CuotaCronogramaDocument> result = new ArrayList<>();
        for (Object item : lista) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> q = (Map<String, Object>) item;
            result.add(CuotaCronogramaDocument.builder()
                    .cuota(toInt(q.get("numeroCuota")))
                    .cuotaNum(toInt(q.get("numeroCuota")))
                    .fechaVencimiento(timestampToDate(q.get("fechaVencimiento")))
                    .monto(toDouble(q.get("montoCuota")))
                    .estado(mapearEstadoCuota(str(q, "estadoPago")))
                    .build());
        }
        return result;
    }

    private String mapearEstadoCuota(String e) {
        if (e == null) return "PENDIENTE";
        return switch (e.toUpperCase()) {
            case "PAGADO", "PAGADA" -> "PAGADA";
            case "VENCIDO", "VENCIDA" -> "VENCIDA";
            default -> "PENDIENTE";
        };
    }

    private String timestampToDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map<?, ?> map) {
            Object seconds = map.get("seconds");
            if (seconds instanceof Number s)
                return Instant.ofEpochSecond(s.longValue())
                        .atZone(ZoneId.of("America/Lima")).toLocalDate().toString();
        }
        if (raw instanceof com.google.cloud.Timestamp ts)
            return ts.toDate().toInstant().atZone(ZoneId.of("America/Lima")).toLocalDate().toString();
        return raw.toString();
    }

    // -------------------------------------------------------------------------
    // Diagnóstico: leer subcollecciones huérfanas
    // -------------------------------------------------------------------------

    public Mono<List<CasoRescatadoDto>> recuperarCasosBorrados() {
        return Mono.fromCallable(this::leerCasosHuerfanos)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<CasoRescatadoDto> leerCasosHuerfanos() throws Exception {
        List<CasoRescatadoDto> resultado = new ArrayList<>();
        for (String uuid : UUIDS_BORRADOS) {
            List<Map<String, Object>> eventos     = leerSubcoleccion(uuid, SUB_EVENTOS);
            List<Map<String, Object>> movimientos = leerSubcoleccion(uuid, SUB_MOVIMIENTOS);
            List<Map<String, Object>> promesas    = leerSubcoleccion(uuid, SUB_PROMESAS);
            DocumentSnapshot contrato = firestore.collection("contratos").document(uuid).get().get();
            resultado.add(new CasoRescatadoDto(uuid, eventos, movimientos, promesas,
                    contrato.exists() ? contrato.getData() : null));
        }
        return resultado;
    }

    private List<Map<String, Object>> leerSubcoleccion(String docId, String sub) throws Exception {
        QuerySnapshot snap = firestore.collection(COLECCION).document(docId).collection(sub).get().get();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snap.getDocuments()) {
            Map<String, Object> data = doc.getData();
            if (data != null) { data.put("_docId", doc.getId()); docs.add(data); }
        }
        return docs;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
    }

    private String str(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private double toDouble(Object o) {
        return (o instanceof Number n) ? n.doubleValue() : 0.0;
    }

    private int toInt(Object o) {
        return (o instanceof Number n) ? n.intValue() : 0;
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    public record CasoRescatadoDto(
            String uuid,
            List<Map<String, Object>> eventos,
            List<Map<String, Object>> movimientos,
            List<Map<String, Object>> promesas,
            Map<String, Object> contratoFirestore
    ) {}

    public record ReconstruirRequest(Map<String, Object> datos) {}
}
