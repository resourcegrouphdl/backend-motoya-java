package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.ObtenerMisComisionesUseCase;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

/**
 * Portal del vendedor: cada vendedor autenticado consulta solo sus propias comisiones.
 * El vendedorId se extrae del token Firebase, nunca de un parámetro de la petición.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mis-comisiones")
@RequiredArgsConstructor
public class MisComisionesController {

    private final ObtenerMisComisionesUseCase obtenerMisComisiones;

    @GetMapping
    public Flux<ComisionVendedor> listar(ServerWebExchange exchange) {
        String vendedorId = exchange.getAttribute("userId");
        if (vendedorId == null || vendedorId.isBlank()) {
            return Flux.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Token no contiene userId"));
        }
        log.info("[MisComisiones] Consulta de comisiones — vendedorId={}", vendedorId);
        return obtenerMisComisiones.ejecutar(vendedorId);
    }
}
