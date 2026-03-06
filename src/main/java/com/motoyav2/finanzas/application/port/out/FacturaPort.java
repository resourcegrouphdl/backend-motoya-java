package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface FacturaPort {
    Flux<Factura> findAll(FiltrosFacturaRequest filtros);
    Mono<Factura> findById(String facturaId);
    Flux<PagoFactura> findPagosByFacturaId(String facturaId);
    Mono<Void> registrarPago(String facturaId, String pagoId, Map<String, Object> campos);
    Mono<Void> actualizarEstadoFactura(String facturaId, Map<String, Object> campos);
    Mono<Void> actualizarVoucherUrl(String facturaId, String pagoId, String url);
}
