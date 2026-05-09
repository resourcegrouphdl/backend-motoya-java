package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.BackfillClienteComisionUseCase;
import com.motoyav2.finanzas.application.port.in.ExportarComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.ListarComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.PagarComisionUseCase;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
public class ComisionController {

    private final ListarComisionesUseCase         listarComisiones;
    private final PagarComisionUseCase            pagarComision;
    private final ExportarComisionesUseCase       exportarComisiones;
    private final BackfillClienteComisionUseCase  backfillClienteComision;

    @GetMapping({"", "/listar"})
    public Flux<ComisionVendedor> listar(
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaFin) {
        return listarComisiones.ejecutar(tiendaId, fechaInicio, fechaFin);
    }

    @GetMapping("/exportar")
    public Mono<ResponseEntity<byte[]>> exportar(
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate fechaFin) {
        return exportarComisiones.ejecutar(tiendaId, fechaInicio, fechaFin)
                .map(bytes -> ResponseEntity.ok()
                        .header("Content-Type", "text/csv; charset=UTF-8")
                        .header("Content-Disposition", "attachment; filename=\"comisiones.csv\"")
                        .body(bytes));
    }

    @PostMapping("/{id}/pagar")
    public Mono<FinanzasActionResponse> pagar(@PathVariable String id) {
        return pagarComision.ejecutar(id)
                .thenReturn(FinanzasActionResponse.ok("Comisión pagada correctamente"));
    }

    @PostMapping("/backfill-clientes")
    public Mono<FinanzasActionResponse> backfillClientes() {
        return backfillClienteComision.ejecutar()
                .map(n -> FinanzasActionResponse.ok("Backfill completado: " + n + " comisiones actualizadas"));
    }
}
