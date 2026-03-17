package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.UsuarioPort;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.AsesorDto;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseUsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UsuarioAdapter implements UsuarioPort {

    private final FirebaseUsuarioRepository repository;

    @Override
    public Mono<AsesorDto> buscarPorId(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) return Mono.empty();
        return repository.findById(usuarioId)
                .map(u -> AsesorDto.builder()
                        .id(u.getId())
                        .nombre(u.getNombre())
                        .email(u.getEmail())
                        .rol(resolverRol(u.getRol(), u.getRoles()))
                        .build());
    }

    private String resolverRol(String rol, java.util.List<String> roles) {
        if (rol != null && !rol.isBlank()) return rol;
        if (roles != null && !roles.isEmpty()) return roles.getFirst();
        return "asesor";
    }
}
