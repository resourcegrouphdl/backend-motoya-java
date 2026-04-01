package com.motoyav2.migracion.infrastructure.adapter.in.web;

import com.motoyav2.migracion.application.dto.*;
import com.motoyav2.migracion.application.service.ContratoBarridoService;
import com.motoyav2.migracion.application.service.MigracionEjecutorService;
import com.motoyav2.migracion.application.service.MigracionImportarService;
import com.motoyav2.migracion.application.service.MigracionStagingService;
import com.motoyav2.migracion.domain.document.MigracionStagingDocument;
import com.motoyav2.shared.security.FirebaseUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Módulo de Migración Asistida desde Google Calendar.
 * Rol requerido: COBRANZAS_SUPERVISOR o ADMIN (validado por FirebaseAuthenticationFilter).
 *
 * Colección propia: migracion-staging (prefijo migracion- para aislamiento)
 * Eliminar este módulo y su colección cuando la migración esté completa.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/migracion")
@Tag(name = "Migración Calendar", description = "Importación asistida desde Google Calendar al sistema de cobranzas")
public class MigracionController {

    private final MigracionImportarService importarService;
    private final MigracionStagingService  stagingService;
    private final MigracionEjecutorService ejecutorService;
    private final ContratoBarridoService   barridoService;

    // ─── 1. Importar desde Google Calendar ───────────────────────────────────

    @PostMapping("/calendar/importar")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Importar clientes desde Google Calendar",
            description = "Lee el calendario 'clientes' de motoyadigital@gmail.com, " +
                    "agrupa eventos por cliente y crea registros en migracion-staging. " +
                    "Idempotente: duplicados son ignorados.")
    public Mono<ImportarCalendarResponse> importarDesdeCalendar() {
        log.info("[Migracion-API] POST /calendar/importar");
        return resolverUsuarioId()
                .flatMap(importarService::importar);
    }

    // ─── 2. Listar staging ────────────────────────────────────────────────────

    @GetMapping("/staging")
    @Operation(
            summary = "Listar registros en staging",
            description = "Devuelve todos los registros. Filtrar por estado: INCOMPLETO | COMPLETO | MIGRADO | ERROR")
    public Flux<MigracionStagingDocument> listarStaging(
            @RequestParam(required = false) String estado) {
        log.debug("[Migracion-API] GET /staging estado={}", estado);
        return stagingService.listar(estado);
    }

    // ─── 3. Completar datos faltantes ─────────────────────────────────────────

    @PutMapping("/staging/{id}")
    @Operation(
            summary = "Completar datos faltantes",
            description = "Guarda contratoId, clienteNombre, clienteDni, telefono y moto. " +
                    "El registro pasa a COMPLETO cuando los 5 campos están presentes.")
    public Mono<MigracionStagingDocument> completarStaging(
            @PathVariable String id,
            @Valid @RequestBody CompletarStagingRequest req) {
        log.info("[Migracion-API] PUT /staging/{} contratoId={}", id, req.contratoId());
        return resolverUsuarioId()
                .flatMap(uid -> stagingService.completar(id, req, uid));
    }

    // ─── 4. Eliminar registro de staging ─────────────────────────────────────

    @DeleteMapping("/staging/{id}")
    @Operation(
            summary = "Eliminar registro de staging",
            description = "Solo se puede eliminar en estado INCOMPLETO, COMPLETO o ERROR. Los MIGRADO son histórico.")
    public Mono<Map<String, Object>> eliminarStaging(
            @PathVariable String id) {
        log.info("[Migracion-API] DELETE /staging/{}", id);
        return stagingService.eliminar(id);
    }

    // ─── 5. Preview del cronograma ────────────────────────────────────────────

    @GetMapping("/staging/{id}/preview-cronograma")
    @Operation(
            summary = "Preview del cronograma",
            description = "Muestra el cronograma que se crearía sin persistir nada. " +
                    "PAGADA / VENCIDA / VIGENTE según colorId y fecha de vencimiento.")
    public Mono<PreviewCronogramaResponse> previewCronograma(
            @PathVariable String id) {
        log.debug("[Migracion-API] GET /staging/{}/preview-cronograma", id);
        return stagingService.previewCronograma(id);
    }

    // ─── 6. Ejecutar migración (un registro) ──────────────────────────────────

    @PostMapping("/staging/{id}/ejecutar")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Migrar un registro al sistema real",
            description = "Batch Write atómico: crea CasoCobranza + MovimientoDeuda + EventoCobranza. " +
                    "Si falla, el registro queda en ERROR para reintento.")
    public Mono<EjecutarMigracionResponse> ejecutarMigracion(
            @PathVariable String id) {
        log.info("[Migracion-API] POST /staging/{}/ejecutar", id);
        return resolverUsuarioId()
                .flatMap(uid -> ejecutorService.ejecutar(id, uid));
    }

    // ─── 7. Ejecutar lote ─────────────────────────────────────────────────────

    @PostMapping("/staging/ejecutar-lote")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Migrar lote de registros",
            description = "Si body es vacío o ids=null, procesa todos los COMPLETO. " +
                    "El fallo de uno no detiene a los demás.")
    public Mono<EjecutarLoteResponse> ejecutarLote(
            @RequestBody(required = false) EjecutarLoteRequest req) {
        log.info("[Migracion-API] POST /staging/ejecutar-lote ids={}", req != null ? req.ids() : "todos");
        return resolverUsuarioId()
                .flatMap(uid -> ejecutorService.ejecutarLote(req, uid));
    }

    // ─── 8. Barrido de contratos → cobranzas-casos ───────────────────────────

    @PostMapping("/contratos/barrido")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Barrido de contratos firmados → cobranzas",
            description = "Lee todos los contratos en estado FIRMADO, ACTIVO o COMPLETADO " +
                    "y crea o actualiza el caso en cobranzas-casos de forma idempotente. " +
                    "No sobrescribe datos ya existentes; sólo completa campos vacíos.")
    public Mono<BarridoContratoResponse> barridoContratos() {
        log.info("[Migracion-API] POST /contratos/barrido");
        return resolverUsuarioId()
                .flatMap(barridoService::ejecutar);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Extrae el uid del token Firebase desde el SecurityContext. Devuelve "SISTEMA" si no hay auth. */
    private Mono<String> resolverUsuarioId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(FirebaseUserDetails.class)
                .map(FirebaseUserDetails::uid)
                .defaultIfEmpty("SISTEMA");
    }

    // ─── Estado del módulo ────────────────────────────────────────────────────

    @GetMapping("/estado")
    @Operation(
            summary = "Estado general de la migración",
            description = "Devuelve conteos por estado para el indicador del panel de administración.")
    public Mono<Map<String, Object>> estadoMigracion() {
        return stagingService.listar(null)
                .collectList()
                .map(docs -> {
                    long total    = docs.size();
                    long migrados = docs.stream().filter(d -> "MIGRADO".equals(d.getEstado())).count();
                    long completos = docs.stream().filter(d -> "COMPLETO".equals(d.getEstado())).count();
                    long incompletos = docs.stream().filter(d -> "INCOMPLETO".equals(d.getEstado())).count();
                    long errores  = docs.stream().filter(d -> "ERROR".equals(d.getEstado())).count();
                    int porcentaje = total > 0 ? (int) ((migrados * 100) / total) : 0;

                    return Map.<String, Object>of(
                            "total",       total,
                            "migrados",    migrados,
                            "completos",   completos,
                            "incompletos", incompletos,
                            "errores",     errores,
                            "porcentaje",  porcentaje,
                            "completa",    total > 0 && migrados == total
                    );
                });
    }
}
