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

    /**
     * Marca una cuenta de pago único como pagada.
     * Body opcional: { "voucherUrl": "...", "gcsPath": "...", "mimeType": "..." }
     */
    @PostMapping("/{id}/pagar")
    public Mono<FinanzasActionResponse> pagar(
            @PathVariable String id,
            @RequestBody(required = false) VoucherBody body) {
        String voucherUrl = body != null ? body.voucherUrl() : null;
        String gcsPath    = body != null ? body.gcsPath()    : null;
        String mimeType   = body != null ? body.mimeType()   : null;
        return pagarCuenta.ejecutar(id, voucherUrl, gcsPath, mimeType)
                .thenReturn(FinanzasActionResponse.ok("Cuenta marcada como pagada"));
    }

    /**
     * Marca una cuota individual como pagada.
     * Body opcional: { "voucherUrl": "...", "gcsPath": "...", "mimeType": "..." }
     */
    @PostMapping("/{cuentaId}/pagar/cuotas/{cuotaId}")
    public Mono<FinanzasActionResponse> pagarCuotaIndividual(
            @PathVariable String cuentaId,
            @PathVariable String cuotaId,
            @RequestBody(required = false) VoucherBody body) {
        String voucherUrl = body != null ? body.voucherUrl() : null;
        String gcsPath    = body != null ? body.gcsPath()    : null;
        String mimeType   = body != null ? body.mimeType()   : null;
        return pagarCuota.ejecutar(cuentaId, cuotaId, voucherUrl, gcsPath, mimeType)
                .thenReturn(FinanzasActionResponse.ok("Cuota pagada"));
    }

    // ── DTO inline ────────────────────────────────────────────────────────────

    public record VoucherBody(
            String voucherUrl,
            String gcsPath,
            String mimeType
    ) {}
}
