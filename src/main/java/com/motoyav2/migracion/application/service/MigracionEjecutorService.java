package com.motoyav2.migracion.application.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.WriteBatch;
import com.motoyav2.migracion.application.dto.EjecutarLoteRequest;
import com.motoyav2.migracion.application.dto.EjecutarLoteResponse;
import com.motoyav2.migracion.application.dto.EjecutarMigracionResponse;
import com.motoyav2.migracion.domain.document.CuotaStagingDocument;
import com.motoyav2.migracion.domain.document.MigracionStagingDocument;
import com.motoyav2.migracion.domain.repository.MigracionStagingRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MigracionEjecutorService {

    private final MigracionStagingRepository repository;

    @Autowired(required = false)
    private com.google.cloud.firestore.Firestore adminFirestore;

    public MigracionEjecutorService(MigracionStagingRepository repository) {
        this.repository = repository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ejecutar un registro
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<EjecutarMigracionResponse> ejecutar(String stagingId, String usuarioId) {
        if (adminFirestore == null) {
            return Mono.error(new IllegalStateException(
                    "Firebase Admin SDK no disponible. Verificar inicialización de Firebase."));
        }
        return repository.findById(stagingId)
                .switchIfEmpty(Mono.error(new NotFoundException("Staging no encontrado: " + stagingId)))
                .flatMap(doc -> {
                    if (!"COMPLETO".equals(doc.getEstado())) {
                        return Mono.error(new BadRequestException(
                                "Solo se pueden migrar registros COMPLETO. Estado actual: " + doc.getEstado()));
                    }
                    return ejecutarBatch(doc, usuarioId);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ejecutar por lote
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<EjecutarLoteResponse> ejecutarLote(EjecutarLoteRequest req, String usuarioId) {
        Flux<MigracionStagingDocument> candidatos;

        if (req != null && req.ids() != null && !req.ids().isEmpty()) {
            candidatos = Flux.fromIterable(req.ids())
                    .flatMap(id -> repository.findById(id).switchIfEmpty(Mono.empty()));
        } else {
            candidatos = repository.findByEstado("COMPLETO");
        }

        return candidatos
                .flatMap(doc -> ejecutarBatch(doc, usuarioId)
                        .map(resp -> new EjecutarLoteResponse.DetalleItem(
                                doc.getId(), resp.status(), resp.contratoId(), resp.errorDetalle()))
                        .onErrorResume(e -> Mono.just(new EjecutarLoteResponse.DetalleItem(
                                doc.getId(), "ERROR", doc.getContratoId(), e.getMessage()))))
                .collectList()
                .map(detalle -> {
                    int migrados = (int) detalle.stream().filter(d -> "OK".equals(d.status())).count();
                    int errores  = detalle.size() - migrados;
                    log.info("[Migracion-Lote] migrados={} errores={}", migrados, errores);
                    return new EjecutarLoteResponse(migrados, errores, detalle);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch Write atómico
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<EjecutarMigracionResponse> ejecutarBatch(MigracionStagingDocument staging, String usuarioId) {
        return Mono.fromCallable(() -> doBatchWrite(staging))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(s -> marcarMigrado(s, usuarioId))
                .map(s -> buildRespuestaOk(s))
                .onErrorResume(e -> {
                    log.error("[Migracion-Batch] Error para {}: {}", staging.getContratoId(), e.getMessage());
                    return marcarError(staging, e.getMessage())
                            .thenReturn(new EjecutarMigracionResponse(
                                    "ERROR", staging.getContratoId(),
                                    "Error al crear el caso.",
                                    e.getMessage()));
                });
    }

    /**
     * Ejecuta el WriteBatch atómico en Firestore Admin SDK.
     * Escribe en: cobranzas-casos, cobranzas-movimientos, cobranzas-eventos
     * NO toca ningún bean del módulo cobranza — aislamiento total.
     */
    private MigracionStagingDocument doBatchWrite(MigracionStagingDocument staging) throws Exception {
        String contratoId = staging.getContratoId();

        // Verificar que el caso no exista ya
        var existente = adminFirestore.collection("cobranzas-casos").document(contratoId).get().get();
        if (existente.exists()) {
            throw new ConflictException("El contratoId " + contratoId + " ya existe en el sistema de cobranzas.");
        }

        LocalDate hoy = LocalDate.now();
        List<CuotaStagingDocument> cuotas = staging.getCronogramaCalendar() != null
                ? staging.getCronogramaCalendar() : List.of();

        // Métricas
        long cuotasPagadas   = cuotas.stream().filter(c -> Boolean.TRUE.equals(c.getPagada())).count();
        long cuotasPendientes = cuotas.size() - cuotasPagadas;
        double saldoActual   = cuotasPendientes * staging.getMontoCuota();
        double capitalOriginal = staging.getCapitalInferido() != null ? staging.getCapitalInferido() : 0.0;

        int diasMora = cuotas.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getPagada()) && c.getFechaVencimiento() != null)
                .filter(c -> LocalDate.parse(c.getFechaVencimiento()).isBefore(hoy))
                .mapToInt(c -> (int) LocalDate.parse(c.getFechaVencimiento()).until(hoy, ChronoUnit.DAYS))
                .max().orElse(0);

        String nivelEstrategia = diasMora >= 90 ? "MORA_CRITICA"
                : diasMora >= 30 ? "MORA_MEDIA" : "MORA_TEMPRANA";

        String fechaPrimeraCuotaImpaga = cuotas.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getPagada()))
                .map(CuotaStagingDocument::getFechaVencimiento)
                .filter(f -> f != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        // Nombre → apellidos/nombres (convención peruana: primeras 2 palabras = apellidos)
        String[] partes = staging.getClienteNombre().trim().split("\\s+");
        String apellidos = partes.length >= 2 ? partes[0] + " " + partes[1] : partes[0];
        String nombres   = partes.length >= 3
                ? String.join(" ", Arrays.copyOfRange(partes, 2, partes.length))
                : (partes.length == 2 ? partes[1] : "");

        Map<String, Object> titular = new LinkedHashMap<>();
        titular.put("nombres", nombres);
        titular.put("apellidos", apellidos);
        titular.put("tipoDocumento", "DNI");
        titular.put("numeroDocumento", staging.getClienteDni());
        titular.put("telefono", staging.getTelefono());

        // Cronograma embebido
        List<Map<String, Object>> cronogramaDoc = cuotas.stream()
                .map(c -> {
                    String estadoCuota;
                    if (Boolean.TRUE.equals(c.getPagada())) {
                        estadoCuota = "PAGADA";
                    } else if (c.getFechaVencimiento() != null
                            && LocalDate.parse(c.getFechaVencimiento()).isBefore(hoy)) {
                        estadoCuota = "VENCIDA";
                    } else {
                        estadoCuota = "PENDIENTE";
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("cuotaNum", c.getCuota());
                    m.put("cuota",    c.getCuota());
                    m.put("monto",    staging.getMontoCuota());
                    m.put("fechaVencimiento", c.getFechaVencimiento() != null ? c.getFechaVencimiento() : "");
                    m.put("estado",   estadoCuota);
                    return m;
                })
                .collect(Collectors.toList());

        // ── 1. CasoCobranza ──────────────────────────────────────────────────
        Map<String, Object> caso = new LinkedHashMap<>();
        caso.put("contratoId",           contratoId);
        caso.put("clienteNombre",         staging.getClienteNombre());
        caso.put("clienteTelefono",       staging.getTelefono());
        caso.put("clienteDni",            staging.getClienteDni());
        caso.put("titular",               titular);
        caso.put("motoDescripcion",       staging.getMoto());
        caso.put("storeId",               null);
        caso.put("nivelEstrategia",       nivelEstrategia);
        caso.put("estadoCaso",            "INTERVENCION_REQUERIDA");
        caso.put("cicloVida",             "ACTIVO");
        caso.put("saldoActual",           saldoActual);
        caso.put("capitalOriginal",       capitalOriginal);
        caso.put("totalPagado",           cuotasPagadas * staging.getMontoCuota());
        caso.put("totalMora",             0.0);
        caso.put("totalCondonado",        0.0);
        caso.put("fechaVencimientoPrimerCuotaImpaga", fechaPrimeraCuotaImpaga);
        caso.put("numeroCuotasTotales",   staging.getTotalCuotas());
        caso.put("numeroCuotasPagadas",   (int) cuotasPagadas);
        caso.put("cronograma",            cronogramaDoc);
        caso.put("ultimaGestion",         com.google.cloud.Timestamp.now());
        caso.put("ultimaGestionResumen",  "Migrado desde Google Calendar");
        caso.put("proximaAccion",         diasMora > 0
                ? "Contactar cliente — mora de " + diasMora + " días"
                : "Contactar cliente — sin mora detectada");
        caso.put("contactoBloqueado",     false);
        caso.put("creadoEn",              com.google.cloud.Timestamp.now());
        caso.put("actualizadoEn",         com.google.cloud.Timestamp.now());
        caso.put("creadoPor",             "MIGRACION_CALENDAR");

        // ── 2. Movimiento SALDO_INICIAL ───────────────────────────────────────
        String movId = UUID.randomUUID().toString();
        Map<String, Object> movimiento = new LinkedHashMap<>();
        movimiento.put("contratoId",    contratoId);
        movimiento.put("tipo",          "SALDO_INICIAL");
        movimiento.put("monto",         capitalOriginal);
        movimiento.put("saldoAnterior", 0.0);
        movimiento.put("saldoNuevo",    capitalOriginal);
        movimiento.put("descripcion",   "Saldo inicial al migrar desde Google Calendar");
        movimiento.put("autorizadoPor", "MIGRACION_CALENDAR");
        movimiento.put("creadoEn",      com.google.cloud.Timestamp.now());

        // ── 3. EventoCobranza ESTADO_CAMBIADO ─────────────────────────────────
        String eventoId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "estadoAnterior", "",
                "estadoNuevo",    "ACTIVO",
                "motivo",         "Migrado desde Google Calendar",
                "stagingId",      staging.getId() != null ? staging.getId() : ""
        );
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("contratoId",    contratoId);
        evento.put("tipo",          "ESTADO_CAMBIADO");
        evento.put("payload",       payload);
        evento.put("usuarioId",     "MIGRACION_CALENDAR");
        evento.put("usuarioNombre", "Sistema de Migración");
        evento.put("automatico",    true);
        evento.put("creadoEn",      com.google.cloud.Timestamp.now());

        // ── Batch Write atómico ───────────────────────────────────────────────
        WriteBatch batch = adminFirestore.batch();

        DocumentReference casoRef = adminFirestore.collection("cobranzas-casos").document(contratoId);
        batch.set(casoRef, caso);

        DocumentReference movRef = adminFirestore.collection("cobranzas-movimientos").document(movId);
        batch.set(movRef, movimiento);

        DocumentReference eventoRef = adminFirestore.collection("cobranzas-eventos").document(eventoId);
        batch.set(eventoRef, evento);

        batch.commit().get(); // bloquea hasta confirmación atómica

        log.info("[Migracion-Batch] Commit exitoso para contratoId={}", contratoId);
        return staging;
    }

    private Mono<MigracionStagingDocument> marcarMigrado(MigracionStagingDocument staging, String usuarioId) {
        staging.setEstado("MIGRADO");
        staging.setContratoIdCreado(staging.getContratoId());
        staging.setMigradoEn(new Date());
        staging.setMigradoPor(usuarioId);
        staging.setActualizadoEn(new Date());
        staging.setActualizadoPor(usuarioId);
        staging.setErrorDetalle(null);
        return repository.save(staging);
    }

    private Mono<Void> marcarError(MigracionStagingDocument staging, String errorMsg) {
        staging.setEstado("ERROR");
        staging.setErrorDetalle(errorMsg);
        staging.setActualizadoEn(new Date());
        return repository.save(staging).then();
    }

    private EjecutarMigracionResponse buildRespuestaOk(MigracionStagingDocument staging) {
        long pendientes = staging.getCronogramaCalendar() != null
                ? staging.getCronogramaCalendar().stream().filter(c -> !Boolean.TRUE.equals(c.getPagada())).count()
                : 0;
        double saldo = pendientes * (staging.getMontoCuota() != null ? staging.getMontoCuota() : 0.0);
        return new EjecutarMigracionResponse(
                "OK",
                staging.getContratoId(),
                "Caso " + staging.getContratoId() + " creado exitosamente. "
                        + pendientes + " cuotas pendientes. Saldo: S/ " + String.format("%.2f", saldo),
                null
        );
    }
}
