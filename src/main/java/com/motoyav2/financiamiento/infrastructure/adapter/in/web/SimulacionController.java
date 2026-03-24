package com.motoyav2.financiamiento.infrastructure.adapter.in.web;

import com.motoyav2.financiamiento.domain.model.SolicitudSimulacion;
import com.motoyav2.financiamiento.domain.service.MotorFinancieroService;
import com.motoyav2.financiamiento.infrastructure.adapter.in.web.request.SimulacionRequest;
import com.motoyav2.financiamiento.infrastructure.adapter.in.web.response.SimulacionResponse;
import com.motoyav2.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * Endpoints públicos de simulación de crédito.
 *
 * <p>Rutas (sin autenticación — ver SecurityConfig):
 * <ul>
 *   <li>POST /api/v1/simulacion           — simula un crédito completo con cronograma</li>
 *   <li>POST /api/v1/simulacion/opciones  — simula múltiples plazos (16/20/24 quincenas)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simulacion")
public class SimulacionController {

    // ── POST /api/v1/simulacion ───────────────────────────────────────────────

    /**
     * Simula un crédito con parámetros específicos.
     * Devuelve cuota fija, cronograma completo y TCEA.
     *
     * @param request parámetros del crédito
     */
    @PostMapping
    public Mono<ResponseEntity<SimulacionResponse>> simular(
            @Valid @RequestBody SimulacionRequest request) {

        return Mono.fromSupplier(() -> {
                    SolicitudSimulacion solicitud = toSolicitud(request);
                    return SimulacionResponse.from(MotorFinancieroService.simular(solicitud));
                })
                .map(ResponseEntity::ok)
                .onErrorMap(IllegalArgumentException.class,
                        e -> new BadRequestException(e.getMessage()));
    }

    // ── POST /api/v1/simulacion/opciones ──────────────────────────────────────

    /**
     * Simula múltiples plazos para el mismo crédito.
     * Ideal para mostrar opciones al cliente en el frontend.
     *
     * <p>Request mínimo:
     * <pre>
     * {
     *   "precioVehiculo": 5000,
     *   "cuotaInicial": 1000,
     *   "tea": 0.60
     * }
     * </pre>
     * Los plazos por defecto son [16, 20, 24] quincenas.
     * Puedes sobreescribirlos enviando {@code numeroCuotas} en el body.
     *
     * @param request       parámetros base (numeroCuotas es ignorado)
     * @param plazos        plazos a simular (query param, default: 16,20,24)
     */
    @PostMapping("/opciones")
    public Mono<ResponseEntity<List<SimulacionResponse>>> simularOpciones(
            @Valid @RequestBody SimulacionRequest request,
            @RequestParam(defaultValue = "16,20,24") List<Integer> plazos) {

        return Mono.fromSupplier(() -> {
                    List<SimulacionResponse> opciones = MotorFinancieroService
                            .simularOpciones(
                                    request.precioVehiculo(),
                                    request.cuotaInicial(),
                                    request.tea(),
                                    plazos)
                            .stream()
                            .map(SimulacionResponse::from)
                            .toList();
                    return opciones;
                })
                .map(ResponseEntity::ok)
                .onErrorMap(IllegalArgumentException.class,
                        e -> new BadRequestException(e.getMessage()));
    }

    // ── POST /api/v1/simulacion/excepcion ─────────────────────────────────────

    /**
     * Simula plazos reducidos usados en evaluaciones de excepción.
     * Cuando no es posible aprobar con plazos estándar (16/20/24),
     * el evaluador puede ofrecer plazos cortos: 8, 10, 12 o 14 quincenas.
     *
     * <p>Cuota más alta, menor riesgo para la empresa.
     */
    @PostMapping("/excepcion")
    public Mono<ResponseEntity<List<SimulacionResponse>>> simularExcepcion(
            @Valid @RequestBody SimulacionRequest request) {

        return Mono.fromSupplier(() ->
                        MotorFinancieroService.simularOpciones(
                                        request.precioVehiculo(),
                                        request.cuotaInicial(),
                                        request.tea(),
                                        List.of(8, 10, 12, 14))
                                .stream()
                                .map(SimulacionResponse::from)
                                .toList())
                .map(ResponseEntity::ok)
                .onErrorMap(IllegalArgumentException.class,
                        e -> new BadRequestException(e.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SolicitudSimulacion toSolicitud(SimulacionRequest r) {
        return SolicitudSimulacion.builder()
                .precioVehiculo(r.precioVehiculo())
                .cuotaInicial(r.cuotaInicial())
                .numeroCuotas(r.numeroCuotas() != null ? r.numeroCuotas() : 24)
                .tea(r.tea())
                .gastosAdministrativos(r.gastosAdministrativos())
                .build();
    }
}
