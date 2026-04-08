package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.Expediente;
import com.motoyav2.evaluacion.domain.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObtenerExpedienteUseCaseImpl implements ObtenerExpedienteUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository clienteRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ReferenciaRepository referenciaRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public Mono<Expediente> ejecutar(String solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(solicitudId)))
                .flatMap(solicitud -> {
                    Mono<com.motoyav2.evaluacion.domain.model.Cliente> titularMono =
                            clienteRepository.findById(solicitud.getTitularId());

                    Mono<com.motoyav2.evaluacion.domain.model.Cliente> fiadorMono = solicitud.tieneFiador()
                            ? clienteRepository.findById(solicitud.getFiadorId())
                            : Mono.empty();

                    Mono<com.motoyav2.evaluacion.domain.model.Vehiculo> vehiculoMono =
                            solicitud.getVehiculoId() != null
                                    ? vehiculoRepository.findById(solicitud.getVehiculoId())
                                            .defaultIfEmpty(nullVehiculo())
                                    : Mono.just(nullVehiculo());

                    Mono<java.util.List<com.motoyav2.evaluacion.domain.model.Referencia>> refsMono =
                            solicitud.getReferenciasIds() != null && !solicitud.getReferenciasIds().isEmpty()
                                    ? referenciaRepository.findByIds(solicitud.getReferenciasIds()).collectList()
                                    : Mono.just(java.util.List.of());

                    Mono<com.motoyav2.evaluacion.domain.model.Usuario> asesorMono = solicitud.tieneAsesorAsignado()
                            ? usuarioRepository.findById(solicitud.getAsesorAsignadoId())
                            : Mono.empty();

                    return Mono.zip(
                            titularMono.defaultIfEmpty(nullCliente()),
                            fiadorMono.defaultIfEmpty(nullCliente()),
                            vehiculoMono,
                            refsMono,
                            asesorMono.defaultIfEmpty(nullUsuario())
                    ).map(tuple -> {
                        var fiadorCliente = isNull(tuple.getT2()) ? null : tuple.getT2();
                        // --- DEBUG FIADOR ---
                        log.info("[DEBUG] solicitud={} fiadorId={} fiadorEncontrado={} archivos={}",
                                solicitudId,
                                solicitud.getFiadorId(),
                                fiadorCliente != null,
                                fiadorCliente != null ? fiadorCliente.getArchivos() : "N/A");
                        // --- END DEBUG ---
                        var vehiculo = isNullV(tuple.getT3()) ? null : tuple.getT3();
                        var titular  = isNull(tuple.getT1()) ? null : tuple.getT1();
                        return Expediente.builder()
                                .solicitud(solicitud)
                                .titular(titular)
                                .fiador(fiadorCliente)
                                .vehiculo(vehiculo)
                                .referencias(tuple.getT4())
                                .asesorAsignado(isNull(tuple.getT5()) ? null : tuple.getT5())
                                .build();
                    });
                });
    }

    // Sentinel nulls — usamos objetos marcadores para Mono.zip (no acepta null)
    private static final com.motoyav2.evaluacion.domain.model.Cliente NULL_CLIENTE =
            com.motoyav2.evaluacion.domain.model.Cliente.builder().id("__null__").build();
    private static final com.motoyav2.evaluacion.domain.model.Usuario NULL_USUARIO =
            com.motoyav2.evaluacion.domain.model.Usuario.builder().id("__null__").build();
    private static final com.motoyav2.evaluacion.domain.model.Vehiculo NULL_VEHICULO =
            com.motoyav2.evaluacion.domain.model.Vehiculo.builder().id("__null__").build();

    private com.motoyav2.evaluacion.domain.model.Cliente nullCliente() { return NULL_CLIENTE; }
    private com.motoyav2.evaluacion.domain.model.Usuario nullUsuario() { return NULL_USUARIO; }
    private com.motoyav2.evaluacion.domain.model.Vehiculo nullVehiculo() { return NULL_VEHICULO; }

    private boolean isNull(com.motoyav2.evaluacion.domain.model.Cliente c) {
        return c != null && "__null__".equals(c.getId());
    }
    private boolean isNull(com.motoyav2.evaluacion.domain.model.Usuario u) {
        return u != null && "__null__".equals(u.getId());
    }
    private boolean isNullV(com.motoyav2.evaluacion.domain.model.Vehiculo v) {
        return v != null && "__null__".equals(v.getId());
    }
}
