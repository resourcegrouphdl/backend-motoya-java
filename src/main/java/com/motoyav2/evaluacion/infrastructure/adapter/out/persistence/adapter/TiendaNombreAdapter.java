package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.domain.port.out.TiendaNombrePort;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository.TiendaProfileRepository;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository.VendedorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TiendaNombreAdapter implements TiendaNombrePort {

    private final TiendaProfileRepository tiendaProfileRepository;
    private final VendedorProfileRepository vendedorProfileRepository;

    @Override
    public Mono<String> resolverNombre(String tiendaId) {
        if (tiendaId == null || tiendaId.isBlank()) return Mono.just("");
        // Algunos documentos migraron el tiendaId con corchetes desde JS (ej: "[uid_abc]" → "uid_abc")
        String cleanId = tiendaId.trim().replaceAll("^\\[+|\\]+$", "").trim();
        if (cleanId.isBlank()) return Mono.just("");
        return tiendaProfileRepository.findById(cleanId)
                .map(doc -> {
                    String name = doc.getBusinessName();
                    return (name != null && !name.isBlank()) ? name : cleanId;
                })
                // Si no existe en tienda_profiles, puede ser que cleanId sea el UID del vendedor.
                // Buscar en vendedor_profiles para obtener el tiendaId real.
                .switchIfEmpty(resolverViaPerfil(cleanId));
    }

    private Mono<String> resolverViaPerfil(String posibleVendedorUid) {
        return vendedorProfileRepository.findById(posibleVendedorUid)
                .filter(vend -> vend.getTiendaId() != null && !vend.getTiendaId().isBlank())
                .flatMap(vend -> tiendaProfileRepository.findById(vend.getTiendaId())
                        .map(tienda -> {
                            String name = tienda.getBusinessName();
                            return (name != null && !name.isBlank()) ? name : vend.getTiendaId();
                        })
                        .defaultIfEmpty(vend.getTiendaId()))
                .defaultIfEmpty(posibleVendedorUid);
    }
}
