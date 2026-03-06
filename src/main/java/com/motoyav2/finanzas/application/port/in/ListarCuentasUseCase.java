package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import reactor.core.publisher.Flux;

public interface ListarCuentasUseCase {
    Flux<CuentaPorPagar> ejecutar(TipoCuenta tipo, EstadoCuenta estado);
}
