package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import com.motoyav2.evaluacion.application.dto.IngresarSolicitudResult;
import com.motoyav2.evaluacion.application.dto.PagedResult;
import com.motoyav2.evaluacion.application.dto.SolicitudTrackingDto;
import com.motoyav2.evaluacion.domain.port.in.ActualizarDocumentosUseCase;
import com.motoyav2.evaluacion.domain.port.in.IngresarSolicitudUseCase;
import com.motoyav2.evaluacion.domain.port.in.ListarSolicitudesVendedorUseCase;
import com.motoyav2.evaluacion.domain.port.in.ReemplazarFiadorUseCase;
import com.motoyav2.evaluacion.domain.port.in.ReemplazarReferenciasUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.request.IngresarSolicitudRequest;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoints públicos consumidos por el frontend del vendedor (mvmotors-front).
 * No requieren rol específico — basta con token Firebase válido (ROLE_USER).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/solicitudes-publicas")
@RequiredArgsConstructor
public class SolicitudPublicaController {

    private final IngresarSolicitudUseCase ingresarSolicitudUseCase;
    private final ListarSolicitudesVendedorUseCase listarSolicitudesVendedorUseCase;
    private final ReemplazarFiadorUseCase reemplazarFiadorUseCase;
    private final ReemplazarReferenciasUseCase reemplazarReferenciasUseCase;
    private final ActualizarDocumentosUseCase actualizarDocumentosUseCase;
    private final SolicitudRepository solicitudRepository;

    // ── POST /ingreso ─────────────────────────────────────────────────────────
    @PostMapping("/ingreso")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<IngresarSolicitudResult> ingresarSolicitud(
            @Valid @RequestBody IngresarSolicitudRequest request) {

        log.info("Ingreso solicitud — titular DNI: {}", request.titular().documentNumber());
        IngresarSolicitudCommand command = toCommand(request);
        return ingresarSolicitudUseCase.ejecutar(command);
    }

    // ── GET /vendedor/{vendedorId} ────────────────────────────────────────────
    @GetMapping("/vendedor/{vendedorId}")
    public Mono<PagedResult<SolicitudTrackingDto>> listarPorVendedor(
            @PathVariable String vendedorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return listarSolicitudesVendedorUseCase.ejecutar(vendedorId, page, size);
    }

    // ── GET /{solicitudId} ────────────────────────────────────────────────────
    @GetMapping("/{solicitudId}")
    public Mono<SolicitudTrackingDto> obtenerPorId(@PathVariable String solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Solicitud no encontrada: " + solicitudId)))
                .map(SolicitudTrackingDto::from);
    }

    // ── PUT /{solicitudId}/fiador ─────────────────────────────────────────────
    @PutMapping("/{solicitudId}/fiador")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reemplazarFiador(
            @PathVariable String solicitudId,
            @Valid @RequestBody IngresarSolicitudRequest.ClienteRequest body) {

        log.info("Reemplazar fiador — solicitud={} DNI={}", solicitudId, body.documentNumber());
        return reemplazarFiadorUseCase.ejecutar(solicitudId, toClienteData(body));
    }

    // ── PUT /{solicitudId}/documentos ─────────────────────────────────────────
    @PutMapping("/{solicitudId}/documentos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> actualizarDocumentos(
            @PathVariable String solicitudId,
            @RequestParam(required = false) String clienteId,
            @RequestBody Map<String, String> archivos) {

        log.info("Actualizar documentos — solicitud={} clienteId={} tipos={}", solicitudId, clienteId, archivos.keySet());
        return actualizarDocumentosUseCase.ejecutar(solicitudId, archivos, clienteId);
    }

    // ── PUT /{solicitudId}/referencias ────────────────────────────────────────
    @PutMapping("/{solicitudId}/referencias")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reemplazarReferencias(
            @PathVariable String solicitudId,
            @Valid @RequestBody List<IngresarSolicitudRequest.ReferenciaRequest> body) {

        log.info("Reemplazar referencias — solicitud={} cantidad={}", solicitudId, body.size());
        List<IngresarSolicitudCommand.ReferenciaData> refs = body.stream()
                .map(r -> new IngresarSolicitudCommand.ReferenciaData(
                        r.nombre(), r.apellidos(), r.telefono(), r.parentesco()))
                .collect(Collectors.toList());
        return reemplazarReferenciasUseCase.ejecutar(solicitudId, refs);
    }

    // ── mapper ────────────────────────────────────────────────────────────────

    private IngresarSolicitudCommand toCommand(IngresarSolicitudRequest r) {
        return new IngresarSolicitudCommand(
                toClienteData(r.titular()),
                r.fiador() != null ? toClienteData(r.fiador()) : null,
                r.referencias().stream().map(ref ->
                        new IngresarSolicitudCommand.ReferenciaData(
                                ref.nombre(), ref.apellidos(), ref.telefono(), ref.parentesco())
                ).collect(Collectors.toList()),
                new IngresarSolicitudCommand.VehiculoData(
                        r.vehiculo().marca(), r.vehiculo().modelo(),
                        r.vehiculo().color(), r.vehiculo().anio()),
                new IngresarSolicitudCommand.FinanciamientoData(
                        r.financiamiento().precioCompraMoto(),
                        r.financiamiento().inicial(),
                        r.financiamiento().plazoQuincenas(),
                        r.financiamiento().montoCuota(),
                        null, null, null, null, null),
                new IngresarSolicitudCommand.VendedorData(
                        r.vendedor().id(), r.vendedor().nombre(), r.vendedor().tienda()),
                r.mensajeOpcional()
        );
    }

    private IngresarSolicitudCommand.ClienteData toClienteData(IngresarSolicitudRequest.ClienteRequest c) {
        return new IngresarSolicitudCommand.ClienteData(
                c.documentType(), c.documentNumber(),
                c.nombres(), c.apellidoPaterno(), c.apellidoMaterno(),
                c.estadoCivil(), c.email(), c.fechaNacimiento(),
                c.departamento(), c.provincia(), c.distrito(), c.direccion(),
                c.ubicacionGPSCasa(),
                c.telefono1(), c.telefono2(),
                c.ocupacion(), c.rangoIngresos(), c.tipoVivienda(),
                c.licenciaConducir(), c.numeroLicencia(),
                c.archivos()
        );
    }
}
