package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.ObtenerMisComisionesUseCase;
import com.motoyav2.finanzas.application.port.out.ComisionPort;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class MisComisionesService implements ObtenerMisComisionesUseCase {

    private final ComisionPort comisionPort;

    @Override
    public Flux<ComisionVendedor> ejecutar(String vendedorId) {
        return comisionPort.findByVendedor(vendedorId);
    }
}
