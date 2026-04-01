package com.motoyav2.migracion.application.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.motoyav2.migracion.application.dto.BarridoContratoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Barrido idempotente: lee todos los contratos en estado FIRMADO/ACTIVO/COMPLETADO
 * y crea o actualiza el caso en cobranzas-casos.
 *
 * Reglas:
 *  - Si no existe el caso → crea (batch atómico: caso + movimiento SALDO_INICIAL + evento)
 *  - Si ya existe → actualiza sólo los campos nulos/vacíos (idempotente)
 *  - Si el contrato no tiene cuotas → omite (SIN_CRONOGRAMA)
 */
@Slf4j
@Service
public class ContratoBarridoService {

    private static final List<String> ESTADOS_CON_CRONOGRAMA = List.of("FIRMADO", "ACTIVO", "COMPLETADO");

    @Autowired(required = false)
    private Firestore adminFirestore;

    public Mono<BarridoContratoResponse> ejecutar(String usuarioId) {
        if (adminFirestore == null) {
            return Mono.error(new IllegalStateException(
                    "Firebase Admin SDK no disponible. Verificar inicialización de Firebase."));
        }
        return Mono.fromCallable(() -> doBarrido(usuarioId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private BarridoContratoResponse doBarrido(String usuarioId) throws Exception {
        List<BarridoContratoResponse.DetalleItem> detalles = new ArrayList<>();
        int creados = 0, actualizados = 0, omitidos = 0, errores = 0;

        QuerySnapshot snap = adminFirestore.collection("contratos")
                .whereIn("estado", ESTADOS_CON_CRONOGRAMA)
                .get().get();

        log.info("[Barrido] Encontrados {} contratos con estado en {}", snap.size(), ESTADOS_CON_CRONOGRAMA);

        for (QueryDocumentSnapshot contratoDoc : snap.getDocuments()) {
            String contratoId = contratoDoc.getId();
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cuotas =
                        (List<Map<String, Object>>) contratoDoc.get("cuotas");

                if (cuotas == null || cuotas.isEmpty()) {
                    detalles.add(new BarridoContratoResponse.DetalleItem(
                            contratoId, "SIN_CRONOGRAMA", "Contrato sin cuotas generadas"));
                    omitidos++;
                    continue;
                }

                DocumentSnapshot casoExistente = adminFirestore
                        .collection("cobranzas-casos").document(contratoId).get().get();

                if (!casoExistente.exists()) {
                    crearCaso(contratoId, contratoDoc, cuotas);
                    detalles.add(new BarridoContratoResponse.DetalleItem(
                            contratoId, "CREADO", "Caso de cobranza creado desde contrato"));
                    creados++;
                } else {
                    boolean updated = actualizarCamposFaltantes(
                            contratoId, contratoDoc, cuotas, casoExistente);
                    if (updated) {
                        detalles.add(new BarridoContratoResponse.DetalleItem(
                                contratoId, "ACTUALIZADO", "Campos faltantes completados"));
                        actualizados++;
                    } else {
                        detalles.add(new BarridoContratoResponse.DetalleItem(
                                contratoId, "OMITIDO", "Caso ya completo"));
                        omitidos++;
                    }
                }
            } catch (Exception e) {
                log.error("[Barrido] Error procesando contratoId={}: {}", contratoId, e.getMessage(), e);
                detalles.add(new BarridoContratoResponse.DetalleItem(
                        contratoId, "ERROR", e.getMessage()));
                errores++;
            }
        }

        log.info("[Barrido] Fin — creados={} actualizados={} omitidos={} errores={}",
                creados, actualizados, omitidos, errores);
        return new BarridoContratoResponse(snap.size(), creados, actualizados, omitidos, errores, detalles);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Crear caso nuevo
    // ─────────────────────────────────────────────────────────────────────────

    private void crearCaso(String contratoId, QueryDocumentSnapshot contratoDoc,
                            List<Map<String, Object>> cuotas) throws Exception {
        LocalDate hoy = LocalDate.now();

        @SuppressWarnings("unchecked")
        Map<String, Object> titularRaw = (Map<String, Object>) contratoDoc.get("titular");
        @SuppressWarnings("unchecked")
        Map<String, Object> fiadorRaw  = (Map<String, Object>) contratoDoc.get("fiador");
        @SuppressWarnings("unchecked")
        Map<String, Object> tiendaRaw  = (Map<String, Object>) contratoDoc.get("tienda");
        @SuppressWarnings("unchecked")
        Map<String, Object> facturaRaw = (Map<String, Object>) contratoDoc.get("facturaVehiculo");

        Map<String, Object> titularMap       = buildTitular(titularRaw);
        Map<String, Object> fiadorMap        = buildFiador(fiadorRaw);
        String              storeId          = tiendaRaw != null ? str(tiendaRaw.get("tiendaId")) : null;
        String              motoDescripcion  = buildMotoDescripcion(facturaRaw);
        Map<String, Object> metricas         = buildMetricas(cuotas, hoy);
        List<Map<String, Object>> cronograma = buildCronograma(cuotas, hoy);

        String clienteNombre = buildNombreCompleto(titularRaw);
        int diasMora = (int) metricas.get("diasMora");

        // ── 1. Caso ──────────────────────────────────────────────────────────
        Map<String, Object> caso = new LinkedHashMap<>();
        // contratoId NO se incluye en el body — es el @DocumentId (document path)
        caso.put("clienteNombre",       clienteNombre);
        caso.put("clienteTelefono",     titularRaw != null ? nvl(str(titularRaw.get("telefono"))) : "");
        caso.put("clienteDni",          titularRaw != null ? nvl(str(titularRaw.get("numeroDocumento"))) : "");
        caso.put("titular",             titularMap);
        if (fiadorMap != null) caso.put("fiador", fiadorMap);
        caso.put("motoDescripcion",     motoDescripcion);
        caso.put("storeId",             storeId);
        caso.put("nivelEstrategia",     metricas.get("nivelEstrategia"));
        caso.put("estadoCaso",          "INTERVENCION_REQUERIDA");
        caso.put("cicloVida",           "ACTIVO");
        caso.put("saldoActual",         metricas.get("saldoActual"));
        caso.put("capitalOriginal",     metricas.get("capitalOriginal"));
        caso.put("totalPagado",         metricas.get("totalPagado"));
        caso.put("totalMora",           0.0);
        caso.put("totalCondonado",      0.0);
        Timestamp tsImpaga = toTimestamp(str(metricas.get("fechaPrimeraCuotaImpaga")));
        if (tsImpaga != null) caso.put("fechaVencimientoPrimerCuotaImpaga", tsImpaga);
        caso.put("numeroCuotasTotales", cuotas.size());
        caso.put("numeroCuotasPagadas", ((Number) metricas.get("cuotasPagadas")).intValue());
        caso.put("cronograma",          cronograma);
        caso.put("ultimaGestion",       Timestamp.now());
        caso.put("ultimaGestionResumen","Migrado desde módulo contratos (barrido)");
        caso.put("proximaAccion",       diasMora > 0
                ? "Contactar cliente — mora de " + diasMora + " días"
                : "Contactar cliente — sin mora detectada");
        caso.put("contactoBloqueado",   false);
        caso.put("creadoEn",            Timestamp.now());
        caso.put("actualizadoEn",       Timestamp.now());
        caso.put("creadoPor",           "MIGRACION_BARRIDO");

        // ── 2. Movimiento SALDO_INICIAL ──────────────────────────────────────
        String movId = UUID.randomUUID().toString();
        Map<String, Object> movimiento = new LinkedHashMap<>();
        movimiento.put("contratoId",    contratoId);
        movimiento.put("tipo",          "SALDO_INICIAL");
        movimiento.put("monto",         metricas.get("capitalOriginal"));
        movimiento.put("saldoAnterior", 0.0);
        movimiento.put("saldoNuevo",    metricas.get("capitalOriginal"));
        movimiento.put("descripcion",   "Saldo inicial — migrado desde contratos");
        movimiento.put("autorizadoPor", "MIGRACION_BARRIDO");
        movimiento.put("creadoEn",      Timestamp.now());

        // ── 3. Evento ESTADO_CAMBIADO ─────────────────────────────────────────
        String eventoId = UUID.randomUUID().toString();
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("contratoId",    contratoId);
        evento.put("tipo",          "ESTADO_CAMBIADO");
        evento.put("payload",       Map.of(
                "estadoAnterior", "",
                "estadoNuevo",    "ACTIVO",
                "motivo",         "Migrado desde contratos — barrido automático"));
        evento.put("usuarioId",     "MIGRACION_BARRIDO");
        evento.put("usuarioNombre", "Sistema de Migración");
        evento.put("automatico",    true);
        evento.put("creadoEn",      Timestamp.now());

        // ── Batch atómico ─────────────────────────────────────────────────────
        WriteBatch batch = adminFirestore.batch();
        batch.set(adminFirestore.collection("cobranzas-casos").document(contratoId), caso);
        batch.set(adminFirestore.collection("cobranzas-movimientos").document(movId), movimiento);
        batch.set(adminFirestore.collection("cobranzas-eventos").document(eventoId), evento);
        batch.commit().get();

        log.info("[Barrido] Caso creado contratoId={} moto='{}' storeId={}", contratoId, motoDescripcion, storeId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actualizar campos faltantes (idempotente)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean actualizarCamposFaltantes(String contratoId, QueryDocumentSnapshot contratoDoc,
                                               List<Map<String, Object>> cuotas,
                                               DocumentSnapshot casoExistente) throws Exception {
        Map<String, Object> updates = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.now();

        // motoDescripcion
        if (isBlankField(casoExistente, "motoDescripcion")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> facturaRaw = (Map<String, Object>) contratoDoc.get("facturaVehiculo");
            String moto = buildMotoDescripcion(facturaRaw);
            if (!moto.isBlank()) updates.put("motoDescripcion", moto);
        }

        // storeId
        if (isBlankField(casoExistente, "storeId")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tiendaRaw = (Map<String, Object>) contratoDoc.get("tienda");
            if (tiendaRaw != null && tiendaRaw.get("tiendaId") != null) {
                updates.put("storeId", str(tiendaRaw.get("tiendaId")));
            }
        }

        // Campos de dirección del titular
        @SuppressWarnings("unchecked")
        Map<String, Object> titularRaw = (Map<String, Object>) contratoDoc.get("titular");
        if (titularRaw != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> titularExistente = (Map<String, Object>) casoExistente.get("titular");
            for (String field : List.of("direccion", "distrito", "provincia", "departamento", "email")) {
                if (isBlankMapField(titularExistente, field)) {
                    String val = str(titularRaw.get(field));
                    if (val != null && !val.isBlank()) {
                        updates.put("titular." + field, val);
                    }
                }
            }
        }

        // Reparar fechaVencimientoPrimerCuotaImpaga si está guardada como String (migración previa)
        Object fechaRaw = casoExistente.get("fechaVencimientoPrimerCuotaImpaga");
        if (fechaRaw instanceof String fechaStr) {
            Timestamp tsReparada = toTimestamp(normalizarFecha(fechaStr));
            if (tsReparada != null) updates.put("fechaVencimientoPrimerCuotaImpaga", tsReparada);
        }

        // Cronograma — actualizar si está vacío
        if (isListEmpty(casoExistente, "cronograma")) {
            Map<String, Object> metricas = buildMetricas(cuotas, hoy);
            updates.put("cronograma",          buildCronograma(cuotas, hoy));
            updates.put("numeroCuotasTotales", cuotas.size());
            updates.put("numeroCuotasPagadas", ((Number) metricas.get("cuotasPagadas")).intValue());
            updates.put("saldoActual",         metricas.get("saldoActual"));
            updates.put("capitalOriginal",     metricas.get("capitalOriginal"));
            updates.put("totalPagado",         metricas.get("totalPagado"));
            updates.put("nivelEstrategia",     metricas.get("nivelEstrategia"));
            Timestamp tsImpagaUpd = toTimestamp(str(metricas.get("fechaPrimeraCuotaImpaga")));
            if (tsImpagaUpd != null) updates.put("fechaVencimientoPrimerCuotaImpaga", tsImpagaUpd);
        }

        if (updates.isEmpty()) return false;

        updates.put("actualizadoEn",  Timestamp.now());
        updates.put("actualizadoPor", "MIGRACION_BARRIDO");
        adminFirestore.collection("cobranzas-casos").document(contratoId).update(updates).get();
        log.info("[Barrido] Caso actualizado contratoId={} campos={}", contratoId, updates.keySet());
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builders
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildTitular(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (raw == null) return m;
        m.put("nombres",         nvl(str(raw.get("nombres"))));
        m.put("apellidos",       nvl(str(raw.get("apellidos"))));
        m.put("tipoDocumento",   nvl(str(raw.get("tipoDocumento")), "DNI"));
        m.put("numeroDocumento", nvl(str(raw.get("numeroDocumento"))));
        m.put("telefono",        nvl(str(raw.get("telefono"))));
        m.put("email",           nvl(str(raw.get("email"))));
        m.put("direccion",       nvl(str(raw.get("direccion"))));
        m.put("distrito",        nvl(str(raw.get("distrito"))));
        m.put("provincia",       nvl(str(raw.get("provincia"))));
        m.put("departamento",    nvl(str(raw.get("departamento"))));
        return m;
    }

    private Map<String, Object> buildFiador(Map<String, Object> raw) {
        if (raw == null) return null;
        String nombres   = str(raw.get("nombres"));
        String apellidos = str(raw.get("apellidos"));
        if ((nombres == null || nombres.isBlank()) && (apellidos == null || apellidos.isBlank())) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombres",         nvl(nombres));
        m.put("apellidos",       nvl(apellidos));
        m.put("tipoDocumento",   nvl(str(raw.get("tipoDocumento")), "DNI"));
        m.put("numeroDocumento", nvl(str(raw.get("numeroDocumento"))));
        m.put("telefono",        nvl(str(raw.get("telefono"))));
        m.put("email",           nvl(str(raw.get("email"))));
        m.put("parentesco",      nvl(str(raw.get("parentesco"))));
        return m;
    }

    private String buildNombreCompleto(Map<String, Object> titularRaw) {
        if (titularRaw == null) return "SIN NOMBRE";
        String nombres   = nvl(str(titularRaw.get("nombres")));
        String apellidos = nvl(str(titularRaw.get("apellidos")));
        return (apellidos + " " + nombres).trim();
    }

    private String buildMotoDescripcion(Map<String, Object> facturaRaw) {
        if (facturaRaw == null) return "";
        List<String> partes = new ArrayList<>();
        String marca  = str(facturaRaw.get("marcaVehiculo"));
        String modelo = str(facturaRaw.get("modeloVehiculo"));
        Object anioObj = facturaRaw.get("anioVehiculo");
        String anio   = anioObj != null ? anioObj.toString() : null;
        String color  = str(facturaRaw.get("colorVehiculo"));
        if (marca  != null && !marca.isBlank())  partes.add(marca);
        if (modelo != null && !modelo.isBlank()) partes.add(modelo);
        if (anio   != null && !anio.isBlank())   partes.add(anio);
        if (color  != null && !color.isBlank())  partes.add(color);
        return String.join(" ", partes);
    }

    private Map<String, Object> buildMetricas(List<Map<String, Object>> cuotas, LocalDate hoy) {
        long cuotasPagadas = cuotas.stream()
                .filter(c -> "PAGADA".equals(str(c.get("estadoPago")))).count();

        double saldoActual = cuotas.stream()
                .filter(c -> !"PAGADA".equals(str(c.get("estadoPago"))))
                .mapToDouble(c -> toDouble(c.get("montoCuota"))).sum();

        // capitalOriginal = suma de capital de todas las cuotas
        double capitalOriginal = cuotas.stream()
                .mapToDouble(c -> toDouble(c.get("montoCapital"))).sum();
        // Fallback: si no hay capital detallado, usar sum montoCuota
        if (capitalOriginal == 0.0) {
            capitalOriginal = cuotas.stream().mapToDouble(c -> toDouble(c.get("montoCuota"))).sum();
        }

        double totalPagado = cuotas.stream()
                .filter(c -> "PAGADA".equals(str(c.get("estadoPago"))))
                .mapToDouble(c -> {
                    double pagado = toDouble(c.get("montoPagado"));
                    return pagado > 0 ? pagado : toDouble(c.get("montoCuota"));
                }).sum();

        int diasMora = cuotas.stream()
                .filter(c -> !"PAGADA".equals(str(c.get("estadoPago"))))
                .filter(c -> c.get("fechaVencimiento") != null)
                .filter(c -> {
                    LocalDate fv = parseFecha(str(c.get("fechaVencimiento")));
                    return fv != null && fv.isBefore(hoy);
                })
                .mapToInt(c -> {
                    LocalDate fv = parseFecha(str(c.get("fechaVencimiento")));
                    return fv != null ? (int) fv.until(hoy, ChronoUnit.DAYS) : 0;
                })
                .max().orElse(0);

        String nivelEstrategia = diasMora >= 90 ? "MORA_CRITICA"
                : diasMora >= 30 ? "MORA_MEDIA" : "MORA_TEMPRANA";

        // Normalizar a YYYY-MM-DD para poder comparar y almacenar consistentemente
        String fechaPrimeraCuotaImpaga = cuotas.stream()
                .filter(c -> !"PAGADA".equals(str(c.get("estadoPago"))))
                .map(c -> normalizarFecha(str(c.get("fechaVencimiento"))))
                .filter(f -> f != null && !f.isBlank())
                .min(Comparator.naturalOrder()).orElse(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cuotasPagadas",             cuotasPagadas);
        m.put("saldoActual",               saldoActual);
        m.put("capitalOriginal",           capitalOriginal);
        m.put("totalPagado",               totalPagado);
        m.put("diasMora",                  diasMora);
        m.put("nivelEstrategia",           nivelEstrategia);
        m.put("fechaPrimeraCuotaImpaga",   fechaPrimeraCuotaImpaga);
        return m;
    }

    private List<Map<String, Object>> buildCronograma(List<Map<String, Object>> cuotas, LocalDate hoy) {
        return cuotas.stream().map(c -> {
            String estadoPago = str(c.get("estadoPago"));
            String estadoCuota;
            if ("PAGADA".equals(estadoPago)) {
                estadoCuota = "PAGADA";
            } else if (c.get("fechaVencimiento") != null) {
                LocalDate fv = parseFecha(str(c.get("fechaVencimiento")));
                estadoCuota = (fv != null && fv.isBefore(hoy)) ? "VENCIDA" : "PENDIENTE";
            } else {
                estadoCuota = "PENDIENTE";
            }
            int num = c.get("numeroCuota") instanceof Number n ? n.intValue() : 0;
            // Normalizar fecha a YYYY-MM-DD (contratos la guardan como ISO datetime)
            String fechaNorm = normalizarFecha(str(c.get("fechaVencimiento")));
            Map<String, Object> cuotaDoc = new LinkedHashMap<>();
            cuotaDoc.put("cuotaNum",         num);
            cuotaDoc.put("cuota",            num);
            cuotaDoc.put("monto",            toDouble(c.get("montoCuota")));
            cuotaDoc.put("fechaVencimiento", fechaNorm);
            cuotaDoc.put("estado",           estadoCuota);
            if ("PAGADA".equals(estadoCuota) && c.get("fechaPago") != null) {
                cuotaDoc.put("fechaPago", normalizarFecha(str(c.get("fechaPago"))));
            }
            return cuotaDoc;
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isBlankField(DocumentSnapshot snap, String field) {
        Object val = snap.get(field);
        return val == null || val.toString().isBlank();
    }

    private boolean isBlankMapField(Map<String, Object> map, String field) {
        if (map == null) return true;
        Object val = map.get(field);
        return val == null || val.toString().isBlank();
    }

    private boolean isListEmpty(DocumentSnapshot snap, String field) {
        Object val = snap.get(field);
        return val == null || (val instanceof List<?> list && list.isEmpty());
    }

    private String str(Object obj) { return obj != null ? obj.toString() : null; }
    private String nvl(String s)   { return s != null ? s : ""; }
    private String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    /**
     * Acepta YYYY-MM-DD y YYYY-MM-DDTHH:mm:ssZ (ISO completo con timezone UTC).
     * Los contratos almacenan fechaVencimiento como datetime ISO completo.
     */
    private LocalDate parseFecha(String iso) {
        if (iso == null || iso.isBlank()) return null;
        if (iso.contains("T")) {
            return java.time.Instant.parse(iso)
                    .atZone(java.time.ZoneId.of("America/Lima"))
                    .toLocalDate();
        }
        return LocalDate.parse(iso);
    }

    /** Normaliza fechaVencimiento a YYYY-MM-DD para almacenar en cobranzas. */
    private String normalizarFecha(String iso) {
        LocalDate d = parseFecha(iso);
        return d != null ? d.toString() : "";
    }

    /** Convierte YYYY-MM-DD a Firestore Timestamp (medianoche Lima). Retorna null si inválido. */
    private Timestamp toTimestamp(String yyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.isBlank()) return null;
        try {
            java.time.Instant instant = LocalDate.parse(yyyyMmDd)
                    .atStartOfDay(java.time.ZoneId.of("America/Lima")).toInstant();
            return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), 0);
        } catch (Exception e) {
            return null;
        }
    }
}
