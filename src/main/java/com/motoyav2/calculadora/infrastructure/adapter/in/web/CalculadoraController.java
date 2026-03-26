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
 * Calculadora Crediticia — endpoints REST.
 *
 * Cumple con transparencia SBS:
 *   - TEA y TCEA divulgados en cada simulación
 *   - Cronograma completo (Resolución SBS N° 11356-2008)
 *   - SOAT y gastos administrativos visibles como líneas separadas
 *   - Comisión identificada como financiada o no financiada
 */
@RestController
@RequestMapping("/api/v1/calculadora")
@RequiredArgsConstructor
@Tag(name = "Calculadora Crediticia", description = "Simulación de créditos y configuración de parámetros (SBS Perú)")
@SecurityRequirement(name = "bearerAuth")
public class CalculadoraController {

    private final SimularCreditoUseCase         simularUseCase;
    private final ObtenerConfiguracionUseCase   obtenerConfigUseCase;
    private final ActualizarConfiguracionUseCase actualizarConfigUseCase;

    @PostMapping("/simular")
    @Operation(summary = "Simular crédito",
               description = "Sistema Francés · WEEKLY o MONTHLY · TCEA por Newton-Raphson · normativa SBS Perú.")
    public Mono<SimularCreditoResponse> simular(@Valid @RequestBody SimularCreditoRequest req) {
        ParametrosSimulacion params = ParametrosSimulacion.builder()
                .precioVehiculo(req.precioVehiculo())
                .soat(req.soat())
                .inicial(req.inicial())
                .numeroCuotas(req.numeroCuotas())
                .frecuencia(req.frecuencia())
                .teaOverride(req.teaOverride())
                .incluirSeguro(req.incluirSeguro())
                .comision(req.comision() != null ? req.comision().toDomain() : null)
                .build();
        return simularUseCase.simular(params).map(SimularCreditoResponse::from);
    }

    @GetMapping("/configuracion")
    @Operation(summary = "Obtener configuración crediticia")
    public Mono<ConfiguracionCrediticiaDto> obtenerConfiguracion() {
        return obtenerConfigUseCase.obtener().map(ConfiguracionCrediticiaDto::from);
    }

    @PutMapping("/configuracion")
    @Operation(summary = "Actualizar configuración crediticia")
    public Mono<ConfiguracionCrediticiaDto> actualizarConfiguracion(
            @Valid @RequestBody ActualizarConfiguracionRequest req,
            @AuthenticationPrincipal FirebaseAuthenticationToken auth) {

        String uid = auth != null ? auth.getPrincipal().uid() : "admin";
        return actualizarConfigUseCase.actualizar(req.toDomain(), uid)
                .map(ConfiguracionCrediticiaDto::from);
    }
}