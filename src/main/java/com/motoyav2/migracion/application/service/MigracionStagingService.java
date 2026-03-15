package com.motoyav2.migracion.application.service;

import com.motoyav2.migracion.application.dto.CompletarStagingRequest;
import com.motoyav2.migracion.application.dto.PreviewCronogramaResponse;
import com.motoyav2.migracion.domain.document.CuotaStagingDocument;
import com.motoyav2.migracion.domain.document.MigracionStagingDocument;
import com.motoyav2.migracion.domain.repository.MigracionStagingRepository;
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
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class MigracionStagingService {

    private final MigracionStagingRepository repository;

    @Autowired(required = false)
    private com.google.cloud.firestore.Firestore adminFirestore;

    public MigracionStagingService(MigracionStagingRepository repository) {
        this.repository = repository;
    }

    public Flux<MigracionStagingDocument> listar(String estado) {
        if (estado != null && !estado.isBlank()) {
            return repository.findByEstado(estado);
        }
        return repository.findAll();
    }

    public Mono<MigracionStagingDocument> completar(String id, CompletarStagingRequest req, String usuarioId) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Staging no encontrado: " + id)))
                .flatMap(doc -> validarContratoIdUnico(req.contratoId(), doc.getContratoId())
                        .thenReturn(doc))
                .flatMap(doc -> {
                    doc.setContratoId(req.contratoId());
                    doc.setClienteNombre(req.clienteNombre());
                    doc.setClienteDni(req.clienteDni());
                    doc.setTelefono(req.telefono());
                    doc.setMoto(req.moto());
                    doc.setErrorDetalle(null);

                    int completitud = calcularCompletitud(doc);
                    doc.setCompletitud(completitud);
                    doc.setEstado(completitud == 100 ? "COMPLETO" : "INCOMPLETO");
                    doc.setActualizadoEn(new Date());
                    doc.setActualizadoPor(usuarioId);

                    return repository.save(doc);
                });
    }

    public Mono<Map<String, Object>> eliminar(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Staging no encontrado: " + id)))
                .flatMap(doc -> {
                    if ("MIGRADO".equals(doc.getEstado())) {
                        return Mono.error(new ConflictException(
                                "No se puede eliminar un registro ya migrado."));
                    }
                    return repository.deleteById(id)
                            .thenReturn(Map.<String, Object>of(
                                    "status", "OK",
                                    "message", "Registro eliminado del staging."));
                });
    }

    public Mono<PreviewCronogramaResponse> previewCronograma(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Staging no encontrado: " + id)))
                .map(this::buildPreview);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica que el contratoId no exista ya en cobranzas-casos.
     * Si es el mismo contratoId que ya tenía el documento (edición), se omite la verificación.
     */
    private Mono<Void> validarContratoIdUnico(String contratoIdNuevo, String contratoIdActual) {
        if (contratoIdNuevo.equals(contratoIdActual)) {
            return Mono.empty();
        }
        if (adminFirestore == null) {
            log.warn("[Migracion] adminFirestore no disponible — omitiendo validación de unicidad");
            return Mono.empty();
        }
        return Mono.fromCallable(() ->
                        adminFirestore.collection("cobranzas-casos")
                                .document(contratoIdNuevo).get().get())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(snap -> {
                    if (snap.exists()) {
                        return Mono.error(new ConflictException(
                                "El contratoId " + contratoIdNuevo + " ya existe en el sistema de cobranzas."));
                    }
                    return Mono.empty();
                });
    }

    private PreviewCronogramaResponse buildPreview(MigracionStagingDocument doc) {
        LocalDate hoy = LocalDate.now();

        var cronograma = doc.getCronogramaCalendar().stream()
                .map(c -> {
                    String estado;
                    if (Boolean.TRUE.equals(c.getPagada())) {
                        estado = "PAGADA";
                    } else if (c.getFechaVencimiento() != null
                            && LocalDate.parse(c.getFechaVencimiento()).isBefore(hoy)) {
                        estado = "VENCIDA";
                    } else {
                        estado = "VIGENTE";
                    }
                    return new PreviewCronogramaResponse.CuotaPreviewDto(
                            c.getCuota(), c.getFechaVencimiento(), estado, doc.getMontoCuota());
                })
                .toList();

        long pagadas    = cronograma.stream().filter(c -> "PAGADA".equals(c.estado())).count();
        long pendientes = cronograma.size() - pagadas;
        double saldo    = pendientes * doc.getMontoCuota();

        // Días mora = desde la primera cuota VENCIDA no pagada hasta hoy
        int diasMora = cronograma.stream()
                .filter(c -> "VENCIDA".equals(c.estado()) && c.fechaVencimiento() != null)
                .mapToInt(c -> (int) LocalDate.parse(c.fechaVencimiento()).until(hoy, ChronoUnit.DAYS))
                .max()
                .orElse(0);

        return new PreviewCronogramaResponse(
                doc.getTotalCuotas(),
                doc.getMontoCuota(),
                doc.getCapitalInferido() != null ? doc.getCapitalInferido() : 0.0,
                (int) pagadas,
                (int) pendientes,
                diasMora,
                saldo,
                cronograma
        );
    }

    private int calcularCompletitud(MigracionStagingDocument doc) {
        int completos = 0;
        if (doc.getContratoId()    != null && !doc.getContratoId().isBlank())    completos++;
        if (doc.getClienteNombre() != null && !doc.getClienteNombre().isBlank()) completos++;
        if (doc.getClienteDni()    != null && !doc.getClienteDni().isBlank())    completos++;
        if (doc.getTelefono()      != null && !doc.getTelefono().isBlank())      completos++;
        if (doc.getMoto()          != null && !doc.getMoto().isBlank())          completos++;
        return completos * 20; // 5 campos × 20 = 100 max
    }
}
