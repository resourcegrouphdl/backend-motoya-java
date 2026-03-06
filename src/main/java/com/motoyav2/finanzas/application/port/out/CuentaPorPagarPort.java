package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import com.motoyav2.finanzas.domain.model.CuotaCuenta;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface CuentaPorPagarPort {
    Flux<CuentaPorPagar> findAll(TipoCuenta tipo, EstadoCuenta estado);
    Mono<CuentaPorPagar> save(CuentaPorPagar cuenta, java.util.List<CuotaCuenta> cuotas);
    Flux<CuotaCuenta> findCuotasByCuentaId(String cuentaId);
    Mono<Void> actualizarCuota(String cuentaId, String cuotaId, Map<String, Object> campos);
    Mono<Void> actualizarCuenta(String cuentaId, Map<String, Object> campos);
}
