package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.VendedorPort;
import com.motoyav2.evaluacion.domain.model.VendedorInfo;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.VendedorProfileFirebaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class VendedorProfileAdapter implements VendedorPort {

    private final VendedorProfileFirebaseRepository vendedorProfileFirebaseRepository;

    @Override
    public Mono<VendedorInfo> obtenerInfoVendedor(String vendedorId) {
        return vendedorProfileFirebaseRepository.findById(vendedorId)
                .map(doc -> new VendedorInfo(
                        doc.getFirstName() + " " + doc.getLastName(),
                        doc.getPhone(),
                        doc.getEmail()
                ));
    }
}
