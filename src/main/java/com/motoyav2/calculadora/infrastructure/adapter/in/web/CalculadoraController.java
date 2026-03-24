package com.motoyav2.calculadora.infrastructure.adapter.in.web;

import com.motoyav2.calculadora.domain.model.ParametrosSimulacion;
import com.motoyav2.calculadora.domain.port.in.ActualizarConfiguracionUseCase;
import com.motoyav2.calculadora.domain.port.in.ObtenerConfiguracionUseCase;
import com.motoyav2.calculadora.domain.port.in.SimularCreditoUseCase;
import com.motoyav2.calculadora.infrastructure.adapter.in.web.dto.ActualizarConfiguracionRequest;
import com.motoyav2.calculadora.infrastructure.adapter.in.web.dto.ConfiguracionCrediticiaDto;
import com.motoyav2.calculadora.infrastructure.adapter.in.web.dto.SimularCreditoRequest;
import com.motoyav2.calculadora.infrastructure.adapter.in.web.dto.SimularCreditoResponse;
import com.motoyav2.shared.security.FirebaseAuthenticationToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Módulo de Calculadora Crediticia.
 *
 * Endpoints públicos (requieren Bearer token Firebase) para:
 *   - Simular créditos con parámetros personalizados
 *   - Consultar y actualizar la configuración crediticia (TEA, plazos, gastos, seguro)
 *
 * Diseñado para uso interno y pruebas desde el panel administrativo.
 * Cumple con transparencia SBS (divulga TEA, TCEA y cronograma completo).
 */
@RestController
@RequestMapping("/api/v1/calculadora")
@RequiredArgsConstructor
@Tag(name = "Calculadora Crediticia", description = "Simulación de créditos y configuración de parámetros (SBS Perú)")
@SecurityRequirement(name = "bearerAuth")
public class CalculadoraController {

    private final SimularCreditoUseCase        simularUseCase;
    private final ObtenerConfiguracionUseCase  obtenerConfigUseCase;
    private final ActualizarConfiguracionUseCase actualizarConfigUseCase;

    // =========================================================================
    // Simulación
    // =========================================================================

    /**
     * Simula un crédito devolviendo: TEA, TCEA, cuota mensual y cronograma completo.
     *
     * El campo {@code teaOverride} permite experimentar con tasas distintas sin
     * modificar la configuración persistida — ideal para encontrar el equilibrio comercial.
     */
    @PostMapping("/simular")
    @Operation(summary = "Simular crédito",
               description = "Calcula cuota, TCEA y cronograma usando Sistema Francés (cuota fija). " +
                             "teaOverride permite probar cualquier TEA sin guardar cambios.")
    public Mono<SimularCreditoResponse> simular(@Valid @RequestBody SimularCreditoRequest req) {
        ParametrosSimulacion params = ParametrosSimulacion.builder()
                .precioVehiculo(req.precioVehiculo())
                .inicial(req.inicial())
                .plazoMeses(req.plazoMeses())
                .teaOverride(req.teaOverride())
                .incluirSeguro(req.incluirSeguro())
                .build();
        return simularUseCase.simular(params).map(SimularCreditoResponse::from);
    }

    // =========================================================================
    // Configuración crediticia
    // =========================================================================

    @GetMapping("/configuracion")
    @Operation(summary = "Obtener configuración crediticia",
               description = "Devuelve los parámetros actuales: gastos admin, TEA por plazo, seguro de desgravamen, etc.")
    public Mono<ConfiguracionCrediticiaDto> obtenerConfiguracion() {
        return obtenerConfigUseCase.obtener().map(ConfiguracionCrediticiaDto::from);
    }

    @PutMapping("/configuracion")
    @Operation(summary = "Actualizar configuración crediticia",
               description = "Persiste nuevos parámetros en Firestore. Los cambios aplican a todas las simulaciones posteriores.")
    public Mono<ConfiguracionCrediticiaDto> actualizarConfiguracion(
            @Valid @RequestBody ActualizarConfiguracionRequest req,
            @AuthenticationPrincipal FirebaseAuthenticationToken auth) {

        String uid = auth != null ? auth.getPrincipal().uid() : "admin";
        return actualizarConfigUseCase.actualizar(req.toDomain(), uid)
                .map(ConfiguracionCrediticiaDto::from);
    }
}
