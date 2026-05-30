package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.application.command.*;
import com.motoyav2.evaluacion.application.dto.PagedResult;
import com.motoyav2.evaluacion.application.dto.SolicitudResumenDto;
import com.motoyav2.evaluacion.domain.enums.Decision;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import com.motoyav2.evaluacion.domain.port.in.*;
import com.motoyav2.evaluacion.domain.port.in.EnviarVerificacionWhatsAppUseCase;
import com.motoyav2.evaluacion.domain.port.in.CorregirNombreDesdeApiUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.request.*;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.response.ExpedienteCompletoResponse;
import com.motoyav2.evaluacion.application.dto.VerificacionIdentidadResult;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/evaluacion")
@RequiredArgsConstructor
public class EvaluacionController {

    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;
    private final ListarSolicitudesUseCase listarSolicitudesUseCase;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;
    private final AsignarAsesorUseCase asignarAsesorUseCase;
    private final EvaluarDocumentosUseCase evaluarDocumentosUseCase;
    private final EvaluarEntrevistaUseCase evaluarEntrevistaUseCase;
    private final VerificarIdentidadUseCase verificarIdentidadUseCase;
    private final RegistrarDecisionFinalUseCase registrarDecisionFinalUseCase;
    private final GenerarCertificadoUseCase generarCertificadoUseCase;
    private final GenerarContratoUseCase generarContratoUseCase;
    private final ObtenerHistorialUseCase obtenerHistorialUseCase;
    private final VerificarReferenciaUseCase verificarReferenciaUseCase;
    private final RechazarReferenciaUseCase rechazarReferenciaUseCase;
    private final AjustarFinanciamientoUseCase ajustarFinanciamientoUseCase;
    private final CorregirNombreDesdeApiUseCase corregirNombreDesdeApiUseCase;
    private final EnviarVerificacionWhatsAppUseCase enviarVerificacionWhatsAppUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ReemplazarFiadorUseCase reemplazarFiadorUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ReemplazarReferenciasUseCase reemplazarReferenciasUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ActualizarEmailClienteUseCase actualizarEmailClienteUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ActualizarDocumentoClienteUseCase actualizarDocumentoClienteUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ActualizarTelefonoClienteUseCase actualizarTelefonoClienteUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ActualizarDireccionClienteUseCase actualizarDireccionClienteUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ActualizarColorVehiculoUseCase actualizarColorVehiculoUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.ReenviarBienvenidaWaUseCase reenviarBienvenidaWaUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.AnalizarSentinelUseCase analizarSentinelUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.EliminarSolicitudUseCase eliminarSolicitudUseCase;
    private final com.motoyav2.evaluacion.domain.port.in.AnalizarRedConexionesUseCase analizarRedConexionesUseCase;
    private final Firestore firestore;

    // ── GET /expediente/{solicitudId} ──────────────────────────────────────
    @GetMapping("/expediente/{solicitudId}")
    public Mono<ExpedienteCompletoResponse> obtenerExpediente(
            @PathVariable String solicitudId) {
        return obtenerExpedienteUseCase.ejecutar(solicitudId)
                .map(ExpedienteCompletoResponse::from)
                .flatMap(this::enriquecerTiendaNombre)
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    /**
     * Resuelve el nombre comercial de la tienda desde tienda_profiles
     * y lo inyecta en DatosVendedorResponse.tiendaNombre.
     * Si el campo tienda contiene el UID del vendedor en lugar del UID de la tienda,
     * lo corrige buscando en vendedor_profiles para obtener el tiendaId real.
     */
    private Mono<ExpedienteCompletoResponse> enriquecerTiendaNombre(ExpedienteCompletoResponse resp) {
        ExpedienteCompletoResponse.SolicitudResponse sol = resp.getSolicitud();
        if (sol == null || sol.getVendedor() == null) return Mono.just(resp);

        String tiendaId = sol.getVendedor().getTienda();
        if (tiendaId == null || tiendaId.isBlank()) return Mono.just(resp);

        return FirestoreUtils.toMono(firestore.collection("tienda_profiles").document(tiendaId).get())
                .flatMap(snap -> {
                    if (snap.exists()) {
                        String businessName = (String) snap.get("businessName");
                        return Mono.just(buildRespConTienda(resp, sol, tiendaId, businessName));
                    }
                    // No encontrado en tienda_profiles: el campo puede ser el UID del vendedor.
                    // Buscar en vendedor_profiles para obtener el tiendaId real.
                    return FirestoreUtils.toMono(firestore.collection("vendedor_profiles").document(tiendaId).get())
                            .flatMap(vendSnap -> {
                                if (!vendSnap.exists()) return Mono.just(resp);
                                String realTiendaId = (String) vendSnap.get("tiendaId");
                                if (realTiendaId == null || realTiendaId.isBlank()) return Mono.just(resp);
                                return FirestoreUtils.toMono(firestore.collection("tienda_profiles").document(realTiendaId).get())
                                        .map(tiendaSnap -> {
                                            String businessName = tiendaSnap.exists()
                                                    ? (String) tiendaSnap.get("businessName") : null;
                                            return buildRespConTienda(resp, sol, realTiendaId, businessName);
                                        });
                            });
                })
                .onErrorResume(ex -> {
                    log.warn("[TIENDA] No se pudo resolver nombre de tienda {}: {}", tiendaId, ex.getMessage());
                    return Mono.just(resp);
                });
    }

    private ExpedienteCompletoResponse buildRespConTienda(
            ExpedienteCompletoResponse resp,
            ExpedienteCompletoResponse.SolicitudResponse sol,
            String resolvedTiendaId,
            String businessName) {

        if (businessName == null || businessName.isBlank()) return resp;

        // Reconstruir vendedor con tiendaId corregido y tiendaNombre resuelto
        ExpedienteCompletoResponse.DatosVendedorResponse vendedorEnriquecido =
                ExpedienteCompletoResponse.DatosVendedorResponse.builder()
                        .id(sol.getVendedor().getId())
                        .nombre(sol.getVendedor().getNombre())
                        .tienda(resolvedTiendaId)
                        .tiendaNombre(businessName)
                        .email(sol.getVendedor().getEmail())
                        .telefono(sol.getVendedor().getTelefono())
                        .build();

                    ExpedienteCompletoResponse.SolicitudResponse solEnriquecida =
                            ExpedienteCompletoResponse.SolicitudResponse.builder()
                                    .id(sol.getId())
                                    .numeroSolicitud(sol.getNumeroSolicitud())
                                    .estado(sol.getEstado())
                                    .prioridad(sol.getPrioridad())
                                    .titularId(sol.getTitularId())
                                    .fiadorId(sol.getFiadorId())
                                    .vehiculoId(sol.getVehiculoId())
                                    .referenciasIds(sol.getReferenciasIds())
                                    .precioCompraMoto(sol.getPrecioCompraMoto())
                                    .inicial(sol.getInicial())
                                    .montoCuota(sol.getMontoCuota())
                                    .plazoQuincenas(sol.getPlazoQuincenas())
                                    .datosFinancieros(sol.getDatosFinancieros())
                                    .vendedor(vendedorEnriquecido)
                                    .vendedorNombre(sol.getVendedorNombre())
                                    .asesorAsignadoId(sol.getAsesorAsignadoId())
                                    .fechaAsignacion(sol.getFechaAsignacion())
                                    .scoreDocumental(sol.getScoreDocumental())
                                    .scoreGarantes(sol.getScoreGarantes())
                                    .scoreEntrevista(sol.getScoreEntrevista())
                                    .scoreFinal(sol.getScoreFinal())
                                    .decisionFinal(sol.getDecisionFinal())
                                    .montoAprobado(sol.getMontoAprobado())
                                    .motivoRechazo(sol.getMotivoRechazo())
                                    .motivoDecision(sol.getMotivoDecision())
                                    .fechaDecisionFinal(sol.getFechaDecisionFinal())
                                    .condicionesAprobacion(sol.getCondicionesAprobacion())
                                    .fortalezasCaso(sol.getFortalezasCaso())
                                    .debilidadesCaso(sol.getDebilidadesCaso())
                                    .resultadoFinal(sol.getResultadoFinal())
                                    .evaluador(sol.getEvaluador())
                                    .certificadoGenerado(sol.getCertificadoGenerado())
                                    .urlCertificado(sol.getUrlCertificado())
                                    .contratoGenerado(sol.getContratoGenerado())
                                    .urlContrato(sol.getUrlContrato())
                                    .observacionesGenerales(sol.getObservacionesGenerales())
                                    .createdAt(sol.getCreatedAt())
                                    .updatedAt(sol.getUpdatedAt())
                                    .build();

        return ExpedienteCompletoResponse.builder()
                .solicitud(solEnriquecida)
                .titular(resp.getTitular())
                .fiador(resp.getFiador())
                .vehiculo(resp.getVehiculo())
                .referencias(resp.getReferencias())
                .datosCompletos(resp.getDatosCompletos())
                .asesorAsignado(resp.getAsesorAsignado())
                .build();
    }

    // ── GET /solicitudes ──────────────────────────────────────────────────
    @GetMapping("/solicitudes")
    public Mono<PagedResult<SolicitudResumenDto>> listarSolicitudes(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listarSolicitudesUseCase.ejecutar(
                new ListarSolicitudesQuery(estado, prioridad, search, tiendaId, page, size));
    }

    // ── GET /tiendas ──────────────────────────────────────────────────────
    // Devuelve la lista de tiendas activas para el selector de filtro.
    @GetMapping("/tiendas")
    public Mono<List<Map<String, String>>> listarTiendas() {
        return FirestoreUtils.toMono(firestore.collection("tienda_profiles").get())
                .map(snap -> snap.getDocuments().stream()
                        .map(doc -> {
                            String businessName = doc.getString("businessName");
                            if (businessName == null || businessName.isBlank()) return null;
                            return Map.of("id", doc.getId(), "nombre", businessName);
                        })
                        .filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparing(m -> m.get("nombre")))
                        .collect(java.util.stream.Collectors.toList()));
    }

    // ── PUT /solicitudes/{solicitudId}/estado ─────────────────────────────
    @PutMapping("/solicitudes/{solicitudId}/estado")
    public Mono<HistorialEstadoResponse> cambiarEstado(
            @PathVariable String solicitudId,
            @Valid @RequestBody CambiarEstadoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        EstadoSolicitud nuevoEstado = EstadoSolicitud.fromFirestoreValue(request.nuevoEstado());
        if (nuevoEstado == null) {
            return Mono.error(new BadRequestException("Estado inválido: " + request.nuevoEstado()));
        }

        String usuarioId = principal != null ? principal.uid() : request.usuarioId();
        String usuarioNombre = principal != null ? principal.email() : request.usuarioNombre();

        return cambiarEstadoUseCase.ejecutar(new CambiarEstadoCommand(
                solicitudId, nuevoEstado,
                usuarioId, usuarioNombre,
                request.motivo()))
                .map(HistorialEstadoResponse::from);
    }

    // ── POST /solicitudes/{solicitudId}/asignar-asesor ────────────────────
    @PostMapping("/solicitudes/{solicitudId}/asignar-asesor")
    public Mono<Map<String, Object>> asignarAsesor(
            @PathVariable String solicitudId,
            @Valid @RequestBody AsignarAsesorRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        String uid = principal != null ? principal.uid() : "sistema";
        String nombre = principal != null ? principal.email() : "sistema";

        return asignarAsesorUseCase.ejecutar(new AsignarAsesorCommand(
                solicitudId, request.asesorId(), request.asesorNombre(),
                request.asesorEmail(), uid, nombre))
                .map(s -> Map.of(
                        "solicitudId", s.getId(),
                        "asesorAsignadoId", request.asesorId(),
                        "estado", s.getEstado().getFirestoreValue(),
                        "message", "Asesor asignado correctamente"));
    }

    // ── PUT /solicitudes/{solicitudId}/evaluacion-documental ──────────────
    @PutMapping("/solicitudes/{solicitudId}/evaluacion-documental")
    public Mono<ResponseEntity<Void>> evaluarDocumentos(
            @PathVariable String solicitudId,
            @RequestBody EvaluarDocumentosRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        String uid = principal != null ? principal.uid() : "sistema";
        String nombre = principal != null ? principal.email() : "sistema";

        EstadoSolicitud estado = request.nuevoEstado() != null
                ? EstadoSolicitud.fromFirestoreValue(request.nuevoEstado())
                : null;

        java.util.Map<String, EvaluarDocumentosCommand.EvaluacionDocumentoData> evalDocs = null;
        if (request.evaluacionDocumentos() != null) {
            evalDocs = request.evaluacionDocumentos().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            e -> new EvaluarDocumentosCommand.EvaluacionDocumentoData(
                                    e.getValue().estado(), e.getValue().observaciones())));
        }

        return evaluarDocumentosUseCase.ejecutar(new EvaluarDocumentosCommand(
                solicitudId, request.scoreDocumental(), request.observaciones(), estado, uid, nombre, evalDocs,
                request.clienteId()))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── PUT /solicitudes/{solicitudId}/decision-final ─────────────────────
    @PutMapping("/solicitudes/{solicitudId}/decision-final")
    public Mono<Map<String, Object>> registrarDecisionFinal(
            @PathVariable String solicitudId,
            @Valid @RequestBody DecisionFinalRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        Decision decision = Decision.fromValue(request.decision());
        if (decision == null) {
            return Mono.error(new BadRequestException("Decisión inválida: " + request.decision()));
        }

        String uid = principal != null ? principal.uid() : "sistema";
        String nombre = principal != null ? principal.email() : "sistema";

        return registrarDecisionFinalUseCase.ejecutar(new DecisionFinalCommand(
                solicitudId, decision, request.motivo(), request.condiciones(),
                request.inicialAjustada(), request.plazoAjustado(),
                request.tea(),
                request.fortalezasCaso(), request.debilidadesCaso(),
                request.evaluador(), uid, nombre))
                .map(s -> Map.of(
                        "solicitudId", s.getId(),
                        "decision", decision.getFirestoreValue(),
                        "estado", s.getEstado().getFirestoreValue(),
                        "message", "Decisión registrada correctamente"));
    }

    // ── GET /certificado/{numeroSolicitud} ────────────────────────────────
    @GetMapping("/certificado/{numeroSolicitud}")
    public Mono<Map<String, Object>> generarCertificado(
            @PathVariable String numeroSolicitud) {
        return generarCertificadoUseCase.ejecutar(numeroSolicitud)
                // ⚠️ El frontend busca exactamente este campo
                .map(url -> Map.of("urlDelCertificadoGenerado", url));
    }

    // ── POST /contrato/{solicitudId} ──────────────────────────────────────
    @PostMapping("/contrato/{solicitudId}")
    public Mono<Map<String, Object>> generarContrato(
            @PathVariable String solicitudId,
            @RequestBody(required = false) ContratoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        String uid = principal != null ? principal.uid() : "sistema";
        Map<String, Object> extras = request != null ? request.camposAdicionales() : null;

        return generarContratoUseCase.ejecutar(new GenerarContratoCommand(solicitudId, uid, extras))
                .map(url -> Map.of("urlContrato", url));
    }

    // ── GET /historial/{solicitudId} ──────────────────────────────────────
    @GetMapping("/historial/{solicitudId}")
    public Mono<List<HistorialEstadoResponse>> obtenerHistorial(
            @PathVariable String solicitudId) {
        return obtenerHistorialUseCase.ejecutar(solicitudId)
                .map(HistorialEstadoResponse::from)
                .collectList();
    }

    // ── POST /referencias/{referenciaId}/verificar ────────────────────────
    @PostMapping("/referencias/{referenciaId}/verificar")
    public Mono<Map<String, Object>> verificarReferencia(
            @PathVariable String referenciaId,
            @Valid @RequestBody VerificarReferenciaRequest request) {

        return verificarReferenciaUseCase.ejecutar(new VerificarReferenciaCommand(
                referenciaId, request.estadoVerificacion(), request.resultadoContacto(),
                request.scoreVerificacion(), request.observaciones(),
                request.actitudDuranteContacto(), request.evaluadorId()))
                .map(r -> Map.of(
                        "referenciaId", r.getId(),
                        "estadoVerificacion", r.getEstadoVerificacion(),
                        "estaVerificada", r.estaVerificada(),
                        "message", "Referencia actualizada"));
    }

    // ── PUT /clientes/{clienteId}/entrevista ──────────────────────────────
    @PutMapping("/clientes/{clienteId}/entrevista")
    public Mono<ResponseEntity<Void>> evaluarEntrevista(
            @PathVariable String clienteId,
            @RequestBody EvaluarEntrevistaRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        String uid    = principal != null ? principal.uid()   : "sistema";
        String nombre = principal != null ? principal.email() : "sistema";

        return evaluarEntrevistaUseCase.ejecutar(new EvaluarEntrevistaCommand(
                clienteId,
                request.solicitudId(),
                request.modalidad(),
                request.plataforma(),
                request.puntualidad(),
                request.presentacionPersonal(),
                request.actitudColaboracion(),
                request.coherenciaRespuestas(),
                request.nivelConfianza(),
                request.scoreEntrevista(),
                request.observacionesCliente(),
                request.observacionesFiador(),
                request.observacionesDomicilio(),
                request.observacionesCapacidadPago(),
                request.hallazgosPositivos(),
                request.hallazgosNegativos(),
                request.recomendacion(),
                request.motivoRecomendacion(),
                request.condiciones(),
                request.esBorrador(),
                uid, nombre))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── POST /clientes/{clienteId}/corregir-nombre ───────────────────────
    @PostMapping("/clientes/{clienteId}/corregir-nombre")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> corregirNombreDesdeApi(@PathVariable String clienteId) {
        return corregirNombreDesdeApiUseCase.ejecutar(clienteId)
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    // ── PATCH /clientes/{clienteId}/email ─────────────────────────────────
    @PatchMapping("/clientes/{clienteId}/email")
    public Mono<ResponseEntity<ExpedienteCompletoResponse.ValidacionEmailResponse>> actualizarEmail(
            @PathVariable String clienteId,
            @RequestBody Map<String, String> body) {
        String nuevoEmail = body.get("email");
        if (nuevoEmail == null) {
            return Mono.error(new BadRequestException("El campo 'email' es requerido"));
        }
        return actualizarEmailClienteUseCase.actualizarEmail(clienteId, nuevoEmail.trim())
                .map(ve -> ResponseEntity.ok(
                        ExpedienteCompletoResponse.ValidacionEmailResponse.builder()
                                .valido(ve.valido())
                                .nivel(ve.nivel())
                                .detalle(ve.detalle())
                                .verificadoEn(ve.verificadoEn())
                                .build()
                ));
    }

    // ── PATCH /clientes/{clienteId}/documento ─────────────────────────────
    @PatchMapping("/clientes/{clienteId}/documento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> actualizarDocumento(
            @PathVariable String clienteId,
            @RequestBody Map<String, String> body) {
        String documentType   = body.get("documentType");
        String documentNumber = body.get("documentNumber");
        if (documentType == null || documentNumber == null) {
            return Mono.error(new BadRequestException("Los campos 'documentType' y 'documentNumber' son requeridos"));
        }
        return actualizarDocumentoClienteUseCase.actualizarDocumento(clienteId, documentType, documentNumber)
                .onErrorMap(RecursoNoEncontradoException.class, e -> new NotFoundException(e.getMessage()));
    }

    // ── PATCH /clientes/{clienteId}/telefono ──────────────────────────────
    @PatchMapping("/clientes/{clienteId}/telefono")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> actualizarTelefono(
            @PathVariable String clienteId,
            @RequestBody Map<String, String> body) {
        String telefono = body.get("telefono1");
        if (telefono == null || telefono.isBlank()) {
            return Mono.error(new BadRequestException("El campo 'telefono1' es requerido"));
        }
        return actualizarTelefonoClienteUseCase.actualizarTelefono(clienteId, telefono);
    }

    // ── POST /solicitudes/{solicitudId}/reenviar-bienvenida-wa ────────────
    @PostMapping("/solicitudes/{solicitudId}/reenviar-bienvenida-wa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reenviarBienvenidaWa(
            @PathVariable String solicitudId,
            @RequestParam(defaultValue = "false") boolean esFiador) {
        return reenviarBienvenidaWaUseCase.reenviar(solicitudId, esFiador);
    }

    // ── POST /clientes/{clienteId}/verificar-identidad ───────────────────
    @PostMapping("/clientes/{clienteId}/verificar-identidad")
    public Mono<VerificacionIdentidadResult> verificarIdentidad(
            @PathVariable String clienteId,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        String uid    = principal != null ? principal.uid()   : "sistema";
        String nombre = principal != null ? principal.email() : "sistema";

        return verificarIdentidadUseCase.ejecutar(
                new VerificarIdentidadCommand(clienteId, uid, nombre))
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    // ── POST /referencias/{referenciaId}/enviar-verificacion ─────────────────
    @PostMapping("/referencias/{referenciaId}/enviar-verificacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> enviarVerificacionWhatsApp(
            @PathVariable String referenciaId,
            @RequestParam String solicitudId) {
        return enviarVerificacionWhatsAppUseCase.ejecutar(referenciaId, solicitudId);
    }

    // ── DELETE /referencias/{referenciaId} ────────────────────────────────
    @DeleteMapping("/referencias/{referenciaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> rechazarReferencia(
            @PathVariable String referenciaId,
            @RequestParam(required = false) String solicitudId) {
        return rechazarReferenciaUseCase.ejecutar(referenciaId, solicitudId);
    }

    // ── PUT /solicitudes/{solicitudId}/financiamiento ─────────────────────
    @PutMapping("/solicitudes/{solicitudId}/financiamiento")
    public Mono<Map<String, Object>> ajustarFinanciamiento(
            @PathVariable String solicitudId,
            @Valid @RequestBody AjustarFinanciamientoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        String uid    = principal != null ? principal.uid()   : "sistema";
        String nombre = principal != null ? principal.email() : "sistema";

        return ajustarFinanciamientoUseCase.ejecutar(new AjustarFinanciamientoCommand(
                solicitudId,
                request.nuevaInicial(),
                request.nuevoPlazo(),
                request.nuevoPrecioMoto(),
                request.tea(),
                uid,
                nombre))
                .onErrorMap(com.motoyav2.shared.exception.BadRequestException.class,
                        e -> new com.motoyav2.shared.exception.BadRequestException(e.getMessage()));
    }

    // ── PATCH /vehiculos/{vehiculoId}/color ──────────────────────────────
    @PatchMapping("/vehiculos/{vehiculoId}/color")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> actualizarColorVehiculo(
            @PathVariable String vehiculoId,
            @RequestBody Map<String, String> body) {
        String color = body.get("color");
        if (color == null || color.isBlank()) {
            return Mono.error(new BadRequestException("El campo 'color' es requerido"));
        }
        return actualizarColorVehiculoUseCase.actualizarColor(vehiculoId, color);
    }

    // ── PATCH /clientes/{clienteId}/direccion ─────────────────────────────
    @PatchMapping("/clientes/{clienteId}/direccion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> actualizarDireccion(
            @PathVariable String clienteId,
            @RequestBody Map<String, String> body) {
        String direccion    = body.get("direccion");
        String distrito     = body.get("distrito");
        String provincia    = body.get("provincia");
        String departamento = body.get("departamento");
        if (direccion == null || direccion.isBlank()) {
            return Mono.error(new BadRequestException("El campo 'direccion' es requerido"));
        }
        return actualizarDireccionClienteUseCase.actualizarDireccion(
                clienteId, direccion, distrito, provincia, departamento);
    }

    // ── POST /solicitudes/{solicitudId}/reemplazar-fiador ─────────────────
    @PostMapping("/solicitudes/{solicitudId}/reemplazar-fiador")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reemplazarFiador(
            @PathVariable String solicitudId,
            @RequestBody IngresarSolicitudRequest.ClienteRequest body) {
        return reemplazarFiadorUseCase.ejecutar(solicitudId, toClienteData(body))
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    // ── POST /solicitudes/{solicitudId}/reemplazar-referencias ─────────────
    @PostMapping("/solicitudes/{solicitudId}/reemplazar-referencias")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reemplazarReferencias(
            @PathVariable String solicitudId,
            @RequestBody List<IngresarSolicitudRequest.ReferenciaRequest> body) {
        List<com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand.ReferenciaData> refs = body.stream()
                .map(r -> new com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand.ReferenciaData(
                        r.nombre(), r.apellidos(), r.telefono(), r.parentesco()))
                .toList();
        return reemplazarReferenciasUseCase.ejecutar(solicitudId, refs)
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    // ── POST /clientes/{clienteId}/sentinel/analizar ──────────────────────
    @PostMapping("/clientes/{clienteId}/sentinel/analizar")
    public Mono<ResponseEntity<com.motoyav2.evaluacion.application.dto.AnalizarSentinelResult>> analizarSentinel(
            @PathVariable String clienteId,
            @Valid @RequestBody AnalizarSentinelRequest body) {
        return analizarSentinelUseCase.analizar(body.toCommand(clienteId))
                .map(ResponseEntity::ok)
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    private com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand.ClienteData toClienteData(
            IngresarSolicitudRequest.ClienteRequest r) {
        return new com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand.ClienteData(
                r.documentType(), r.documentNumber(), r.nombres(), r.apellidoPaterno(), r.apellidoMaterno(),
                r.estadoCivil(), r.email(), r.fechaNacimiento(), r.departamento(), r.provincia(),
                r.distrito(), r.direccion(), r.ubicacionGPSCasa(), r.telefono1(), r.telefono2(),
                r.ocupacion(), r.rangoIngresos(), r.tipoVivienda(), r.licenciaConducir(), r.numeroLicencia(),
                r.sexo(), r.tipoTrabajo(), r.relacionConFiador(), r.relacionConTitular(),
                r.nacionalidad(), r.estadoResidencia(),
                r.archivos());
    }

    // ── Inner response type ───────────────────────────────────────────────

    public record HistorialEstadoResponse(
            String id,
            String solicitudId,
            String estadoAnterior,
            String estadoNuevo,
            com.google.cloud.Timestamp fechaCambio,
            String usuarioId,
            String usuarioNombre,
            String motivo
    ) {
        static HistorialEstadoResponse from(HistorialEstado h) {
            return new HistorialEstadoResponse(
                    h.getId(), h.getSolicitudId(),
                    h.getEstadoAnterior() != null ? h.getEstadoAnterior().getFirestoreValue() : null,
                    h.getEstadoNuevo() != null ? h.getEstadoNuevo().getFirestoreValue() : null,
                    h.getFechaCambio(), h.getUsuarioId(), h.getUsuarioNombre(), h.getMotivo()
            );
        }
    }

    // ── DELETE /{solicitudId} ──────────────────────────────────────────────────
    @DeleteMapping("/{solicitudId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> eliminarSolicitud(@PathVariable String solicitudId) {
        return eliminarSolicitudUseCase.eliminar(solicitudId)
                .onErrorMap(com.motoyav2.shared.exception.NotFoundException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    // ── GET /expediente/{solicitudId}/red-conexiones ───────────────────────
    @GetMapping("/expediente/{solicitudId}/red-conexiones")
    public Mono<List<com.motoyav2.evaluacion.infrastructure.adapter.in.web.response.HallazgoRedConexionesDto>> analizarRedConexiones(
            @PathVariable String solicitudId) {
        return analizarRedConexionesUseCase.analizar(solicitudId)
                .map(hallazgos -> hallazgos.stream()
                        .map(com.motoyav2.evaluacion.infrastructure.adapter.in.web.response.HallazgoRedConexionesDto::from)
                        .toList());
    }
}
