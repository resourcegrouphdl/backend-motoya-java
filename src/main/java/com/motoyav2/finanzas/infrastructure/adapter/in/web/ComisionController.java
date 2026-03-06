package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.ListarComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.PagarComisionUseCase;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
public class ComisionController {

    private final ListarComisionesUseCase listarComisiones;
    private final PagarComisionUseCase pagarComision;

    @GetMapping
    public Flux<ComisionVendedor> listar(
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return listarComisiones.ejecutar(tiendaId, fechaInicio, fechaFin);
    }

    @PostMapping("/{id}/pagar")
    public Mono<FinanzasActionResponse> pagar(@PathVariable String id) {
        return pagarComision.ejecutar(id)
                .thenReturn(FinanzasActionResponse.ok("Comisión pagada correctamente"));
    }
}
