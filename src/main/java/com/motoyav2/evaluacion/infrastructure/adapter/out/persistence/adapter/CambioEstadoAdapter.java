package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.port.out.CambioEstadoPort;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.HistorialEstadoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.HistorialEstadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CambioEstadoAdapter implements CambioEstadoPort {

    private final HistorialEstadoRepository repository;

    @Override
    public Mono<Void> registrar(String solicitudId,
                                 EstadoSolicitud estadoAnterior,
                                 EstadoSolicitud estadoNuevo,
                                 String usuarioId,
                                 String usuarioNombre,
                                 String motivo) {
        HistorialEstadoDocument doc = HistorialEstadoDocument.builder()
                .solicitudId(solicitudId)
                .estadoAnterior(estadoAnterior != null ? estadoAnterior.name() : null)
                .estadoNuevo(estadoNuevo.name())
                .fechaCambio(Timestamp.now())
                .usuarioId(usuarioId)
                .usuarioNombre(usuarioNombre)
                .motivo(motivo)
                .build();

        return repository.save(doc).then();
    }
}
