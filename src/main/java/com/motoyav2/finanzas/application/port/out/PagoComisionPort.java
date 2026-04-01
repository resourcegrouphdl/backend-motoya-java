package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.application.port.in.command.ConfirmarPagoComisionCommand;
import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PagoComisionPort {
    Flux<PagoComisionVendedor> findAll(String vendedorId, String tiendaId, String estado);
    Mono<PagoComisionVendedor> findById(String id);
    Mono<Void> confirmar(ConfirmarPagoComisionCommand cmd);
    Mono<Void> actualizarComprobanteUrl(String pagoId, String comprobanteUrl);
    Mono<Integer> generarPagosQuincenales();
}
