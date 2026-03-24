package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.command.*;
import com.motoyav2.evaluacion.application.dto.PagedResult;
import com.motoyav2.evaluacion.application.dto.SolicitudResumenDto;
import com.motoyav2.evaluacion.domain.enums.Decision;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import com.motoyav2.evaluacion.domain.port.in.*;
import com.motoyav2.evaluacion.domain.port.in.CorregirNombreDesdeApiUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.request.*;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.response.ExpedienteCompletoResponse;
import com.motoyav2.evaluacion.application.dto.VerificacionIdentidadResult;
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

    // ── GET /expediente/{solicitudId} ──────────────────────────────────────
    @GetMapping("/expediente/{solicitudId}")
    public Mono<ExpedienteCompletoResponse> obtenerExpediente(
            @PathVariable String solicitudId) {
        return obtenerExpedienteUseCase.ejecutar(solicitudId)
                .map(ExpedienteCompletoResponse::from)
                .onErrorMap(RecursoNoEncontradoException.class,
                        e -> new NotFoundException(e.getMessage()));
    }

    // ── GET /solicitudes ──────────────────────────────────────────────────
    @GetMapping("/solicitudes")
    public Mono<PagedResult<SolicitudResumenDto>> listarSolicitudes(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listarSolicitudesUseCase.ejecutar(
                new ListarSolicitudesQuery(estado, prioridad, search, page, size));
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
                request.tea(),
                uid,
                nombre))
                .onErrorMap(com.motoyav2.shared.exception.BadRequestException.class,
                        e -> new com.motoyav2.shared.exception.BadRequestException(e.getMessage()));
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
}
