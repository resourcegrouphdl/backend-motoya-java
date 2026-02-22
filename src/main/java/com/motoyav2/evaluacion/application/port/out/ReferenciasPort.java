package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ReferenciasPort {

    Flux<ReferenciasDelTitular> buscarPorIds(List<String> referenciasIds);
}
