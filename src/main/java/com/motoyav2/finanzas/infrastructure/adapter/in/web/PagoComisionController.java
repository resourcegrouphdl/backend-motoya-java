package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.*;
import com.motoyav2.finanzas.application.port.in.command.ConfirmarPagoComisionCommand;
import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/comisiones/pagos")
@RequiredArgsConstructor
public class PagoComisionController {

    private final ListarPagosComisionUseCase    listar;
    private final ObtenerPagoComisionUseCase    obtener;
    private final ConfirmarPagoComisionUseCase  confirmar;
    private final GenerarPagosQuincenalesUseCase generar;

    /** GET /api/comisiones/pagos?vendedorId=&tiendaId=&estado= */
    @GetMapping
    public Flux<PagoComisionVendedor> listar(
            @RequestParam(required = false) String vendedorId,
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) String estado) {
        return listar.ejecutar(vendedorId, tiendaId, estado);
    }

    /** GET /api/comisiones/pagos/{id} */
    @GetMapping("/{id}")
    public Mono<PagoComisionVendedor> obtener(@PathVariable String id) {
        return obtener.ejecutar(id);
    }

    /** POST /api/comisiones/pagos/{id}/confirmar */
    @PostMapping("/{id}/confirmar")
    public Mono<FinanzasActionResponse> confirmar(
            @PathVariable String id,
            @RequestBody ConfirmarPagoRequest body) {
        ConfirmarPagoComisionCommand cmd = new ConfirmarPagoComisionCommand(
                id,
                body.metodoPago(),
                body.entidadBancaria(),
                body.cuentaDestino(),
                body.numeroOperacion(),
                body.voucherUrl(),
                body.voucherGcsPath(),
                body.registradoPor()
        );
        return confirmar.ejecutar(cmd)
                .thenReturn(FinanzasActionResponse.ok("Pago de comisión confirmado"));
    }

    /** POST /api/comisiones/pagos/generar — trigger manual */
    @PostMapping("/generar")
    public Mono<FinanzasActionResponse> generar() {
        return generar.ejecutar()
                .map(n -> FinanzasActionResponse.ok("Pagos quincenales generados: " + n));
    }

    // ── DTO inline ────────────────────────────────────────────────────────────

    public record ConfirmarPagoRequest(
            @NotBlank String metodoPago,
            String entidadBancaria,
            String cuentaDestino,
            @NotBlank String numeroOperacion,
            @NotBlank String voucherUrl,
            String voucherGcsPath,
            String registradoPor
    ) {}
}
