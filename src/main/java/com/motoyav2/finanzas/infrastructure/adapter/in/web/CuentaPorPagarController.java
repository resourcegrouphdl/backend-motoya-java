package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.*;
import com.motoyav2.finanzas.application.port.in.command.CrearCuentaCommand;
import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.CrearCuentaRequest;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/cuentas-pagar")
@RequiredArgsConstructor
public class CuentaPorPagarController {

    private final ListarCuentasUseCase listarCuentas;
    private final CrearCuentaUseCase crearCuenta;
    private final PagarCuentaUseCase pagarCuenta;
    private final PagarCuotaUseCase pagarCuota;

    @GetMapping
    public Flux<CuentaPorPagar> listar(
            @RequestParam(required = false) TipoCuenta tipo,
            @RequestParam(required = false) EstadoCuenta estado) {
        return listarCuentas.ejecutar(tipo, estado);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CuentaPorPagar> crear(@Valid @RequestBody CrearCuentaRequest request) {
        CrearCuentaCommand command = new CrearCuentaCommand(
                request.getTipo(),
                request.getProveedor(),
                request.getDescripcion(),
                request.getNumeroDocumento(),
                request.getMontoTotal(),
                request.getNumeroCuotas(),
                request.getFechaVencimiento(),
                null
        );
        return crearCuenta.ejecutar(command);
    }

    @PostMapping("/{id}/pagar")
    public Mono<FinanzasActionResponse> pagar(@PathVariable String id) {
        return pagarCuenta.ejecutar(id)
                .thenReturn(FinanzasActionResponse.ok("Cuenta marcada como pagada"));
    }

    @PostMapping("/{cuentaId}/pagar/cuotas/{cuotaId}")
    public Mono<FinanzasActionResponse> pagarCuotaIndividual(
            @PathVariable String cuentaId,
            @PathVariable String cuotaId) {
        return pagarCuota.ejecutar(cuentaId, cuotaId)
                .thenReturn(FinanzasActionResponse.ok("Cuota pagada"));
    }
}
