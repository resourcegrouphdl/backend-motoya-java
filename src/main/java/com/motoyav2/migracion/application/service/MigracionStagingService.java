package com.motoyav2.migracion.application.service;

import com.motoyav2.migracion.application.dto.ActualizarCronogramaRequest;
import com.motoyav2.migracion.application.dto.CompletarStagingRequest;
import com.motoyav2.migracion.application.dto.PreviewCronogramaResponse;
import com.motoyav2.migracion.domain.document.CuotaStagingDocument;
import com.motoyav2.migracion.domain.document.MigracionStagingDocument;
import com.motoyav2.migracion.domain.document.ReferenciaDocument;
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
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    doc.setEmail(req.email());
                    doc.setMoto(req.moto());
                    doc.setStoreId(req.storeId());
                    doc.setDireccion(req.direccion());
                    doc.setDistrito(req.distrito());
                    doc.setProvincia(req.provincia());
                    doc.setDepartamento(req.departamento());
                    doc.setFiadorNombre(req.fiadorNombre());
                    doc.setFiadorApellidos(req.fiadorApellidos());
                    doc.setFiadorTipoDocumento(req.fiadorTipoDocumento());
                    doc.setFiadorDni(req.fiadorDni());
                    doc.setFiadorTelefono(req.fiadorTelefono());
                    doc.setFiadorEmail(req.fiadorEmail());
                    doc.setFiadorParentesco(req.fiadorParentesco());

                    // Referencias y observaciones
                    if (req.referencias() != null) {
                        List<ReferenciaDocument> refs = req.referencias().stream()
                                .map(r -> ReferenciaDocument.builder()
                                        .nombre(r.nombre())
                                        .telefono(r.telefono())
                                        .parentesco(r.parentesco())
                                        .direccion(r.direccion())
                                        .build())
                                .toList();
                        doc.setReferencias(refs);
                    }
                    if (req.observaciones() != null) {
                        doc.setObservaciones(req.observaciones());
                    }
                    doc.setErrorDetalle(null);

                    int completitud = calcularCompletitud(doc);
                    doc.setCompletitud(completitud);
                    doc.setEstado(completitud == 100 ? "COMPLETO" : "INCOMPLETO");
                    doc.setActualizadoEn(new Date());
                    doc.setActualizadoPor(usuarioId);

                    return repository.save(doc);
                });
    }

    public Mono<MigracionStagingDocument> actualizarCronograma(
            String id, ActualizarCronogramaRequest req, String usuarioId) {

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Staging no encontrado: " + id)))
                .flatMap(doc -> {
                    if ("MIGRADO".equals(doc.getEstado())) {
                        return Mono.error(new ConflictException(
                                "No se puede editar el cronograma de un registro ya migrado."));
                    }

                    // Actualiza montoCuota global si se proporcionó
                    if (req.montoCuota() != null && req.montoCuota() > 0) {
                        doc.setMontoCuota(req.montoCuota());
                    }
                    double montoGlobal = doc.getMontoCuota() != null ? doc.getMontoCuota() : 0.0;

                    List<CuotaStagingDocument> cuotas = req.cronograma().stream()
                            .map(c -> CuotaStagingDocument.builder()
                                    .cuota(c.cuota())
                                    .fechaVencimiento(c.fechaVencimiento())
                                    .pagada(c.pagada())
                                    .monto(c.monto())
                                    .tituloOriginal("Corrección manual")
                                    .build())
                            .sorted(java.util.Comparator.comparingInt(CuotaStagingDocument::getCuota))
                            .toList();

                    doc.setCronogramaCalendar(cuotas);
                    doc.setTotalCuotas(cuotas.size());

                    List<Integer> pagadas = cuotas.stream()
                            .filter(c -> Boolean.TRUE.equals(c.getPagada()))
                            .map(CuotaStagingDocument::getCuota)
                            .toList();
                    doc.setCuotasPagadas(pagadas);

                    // Recalcular capitalInferido con montos individuales
                    double capital = cuotas.stream()
                            .mapToDouble(c -> c.getMonto() != null ? c.getMonto() : montoGlobal)
                            .sum();
                    doc.setCapitalInferido(capital);

                    // Fecha inicio = cuota 1
                    cuotas.stream()
                            .filter(c -> c.getCuota() != null && c.getCuota() == 1)
                            .map(CuotaStagingDocument::getFechaVencimiento)
                            .filter(f -> f != null)
                            .findFirst()
                            .ifPresent(doc::setFechaInicio);

                    doc.setActualizadoEn(new Date());
                    doc.setActualizadoPor(usuarioId);

                    return repository.save(doc);
                });
    }

    /**
     * Genera el siguiente contratoId disponible con formato MIG-{YYYY}-{NNN}.
     * Escanea staging + cobranzas-casos para evitar duplicados.
     */
    public Mono<String> generarSiguienteContratoId() {
        int anio = Year.now().getValue();
        String prefijo = "MIG-" + anio + "-";
        Pattern patron = Pattern.compile("MIG-" + anio + "-(\\d+)");

        Mono<Integer> maxStaging = repository.findAll()
                .mapNotNull(MigracionStagingDocument::getContratoId)
                .filter(id -> id.startsWith(prefijo))
                .map(id -> {
                    Matcher m = patron.matcher(id);
                    return m.matches() ? Integer.parseInt(m.group(1)) : 0;
                })
                .reduce(0, Integer::max);

        Mono<Integer> maxCasos = (adminFirestore == null) ? Mono.just(0) :
                Mono.fromCallable(() ->
                        adminFirestore.collection("cobranzas-casos")
                                .get().get().getDocuments().stream()
                                .map(d -> d.getId())
                                .filter(id -> id.startsWith(prefijo))
                                .map(id -> {
                                    Matcher m = patron.matcher(id);
                                    return m.matches() ? Integer.parseInt(m.group(1)) : 0;
                                })
                                .reduce(0, Integer::max))
                        .subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(maxStaging, maxCasos)
                .map(t -> {
                    int siguiente = Math.max(t.getT1(), t.getT2()) + 1;
                    return String.format("%s%03d", prefijo, siguiente);
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
        double montoGlobal = doc.getMontoCuota() != null ? doc.getMontoCuota() : 0.0;

        if (doc.getCronogramaCalendar() == null || doc.getCronogramaCalendar().isEmpty()) {
            return new PreviewCronogramaResponse(
                    doc.getTotalCuotas() != null ? doc.getTotalCuotas() : 0,
                    montoGlobal, 0.0, 0, 0, 0, 0.0, List.of());
        }

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
                    double monto = c.getMonto() != null ? c.getMonto() : montoGlobal;
                    return new PreviewCronogramaResponse.CuotaPreviewDto(
                            c.getCuota(), c.getFechaVencimiento(), estado, monto);
                })
                .toList();

        long pagadas    = cronograma.stream().filter(c -> "PAGADA".equals(c.estado())).count();
        double saldo    = cronograma.stream()
                .filter(c -> !"PAGADA".equals(c.estado()))
                .mapToDouble(PreviewCronogramaResponse.CuotaPreviewDto::monto)
                .sum();

        // Días mora = desde la primera cuota VENCIDA no pagada hasta hoy
        int diasMora = cronograma.stream()
                .filter(c -> "VENCIDA".equals(c.estado()) && c.fechaVencimiento() != null)
                .mapToInt(c -> (int) LocalDate.parse(c.fechaVencimiento()).until(hoy, ChronoUnit.DAYS))
                .max()
                .orElse(0);

        return new PreviewCronogramaResponse(
                doc.getTotalCuotas(),
                montoGlobal,
                doc.getCapitalInferido() != null ? doc.getCapitalInferido() : 0.0,
                (int) pagadas,
                (int) (cronograma.size() - pagadas),
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
