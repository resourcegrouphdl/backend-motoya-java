package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.ListarFacturasUseCase;
import com.motoyav2.finanzas.application.port.in.ObtenerFacturaUseCase;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
public class FacturaController {

    private final ListarFacturasUseCase listarFacturas;
    private final ObtenerFacturaUseCase obtenerFactura;

    @GetMapping
    public Flux<Factura> listar(
            @RequestParam(required = false) String tiendaId,
            @RequestParam(required = false) EstadoPago estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {

        FiltrosFacturaRequest filtros = new FiltrosFacturaRequest();
        filtros.setTiendaId(tiendaId);
        filtros.setEstado(estado);
        filtros.setFechaDesde(fechaDesde);
        filtros.setFechaHasta(fechaHasta);

        return listarFacturas.ejecutar(filtros);
    }

    @GetMapping("/{id}")
    public Mono<Factura> obtener(@PathVariable String id) {
        return obtenerFactura.ejecutar(id);
    }

    @GetMapping("/{id}/cronograma")
    public Mono<Factura> cronograma(@PathVariable String id) {
        return obtenerFactura.ejecutar(id);
    }
}
