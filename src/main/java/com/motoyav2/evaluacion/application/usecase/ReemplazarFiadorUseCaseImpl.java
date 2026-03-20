package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.port.in.ReemplazarFiadorUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReemplazarFiadorUseCaseImpl implements ReemplazarFiadorUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository clienteRepository;

    @Override
    public Mono<Void> ejecutar(String solicitudId, IngresarSolicitudCommand.ClienteData fiador) {
        Timestamp ahora = Timestamp.now();

        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(solicitud -> {
                    if (solicitud.getEstado() != EstadoSolicitud.FIADOR_RECHAZADO) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "La solicitud no está en estado fiador_rechazado"));
                    }

                    // upsert fiador client
                    return upsertCliente(fiador, solicitud.getCodigoDeSolicitud(), ahora)
                            .flatMap(nuevoFiadorId -> {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("fiadorId", nuevoFiadorId);
                                updates.put("estado", EstadoSolicitud.EVALUACION_GARANTES.getFirestoreValue());
                                updates.put("updatedAt", ahora);
                                log.info("Fiador reemplazado en solicitud {} → nuevo fiadorId={}", solicitudId, nuevoFiadorId);
                                return solicitudRepository.updateFields(solicitudId, updates);
                            });
                });
    }

    private Mono<String> upsertCliente(IngresarSolicitudCommand.ClienteData data,
                                        String codigoDeSolicitud, Timestamp ahora) {
        return clienteRepository.findByDocumentNumber(data.documentNumber())
                .flatMap(existente -> {
                    Map<String, Object> updates = buildClienteMap(data, codigoDeSolicitud, ahora);
                    updates.put("updatedAt", ahora);
                    return clienteRepository.updateFields(existente.getId(), updates)
                            .thenReturn(existente.getId());
                })
                .switchIfEmpty(
                        clienteRepository.create(buildClienteMap(data, codigoDeSolicitud, ahora))
                );
    }

    private Map<String, Object> buildClienteMap(IngresarSolicitudCommand.ClienteData d,
                                                  String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipo", "fiador");
        m.put("tipoCliente", "fiador");
        m.put("codigoDeSolicitud", codigo);
        m.put("documentType", d.documentType());
        m.put("documentNumber", d.documentNumber());
        m.put("nombres", d.nombres());
        m.put("apellidoPaterno", d.apellidoPaterno());
        m.put("apellidoMaterno", d.apellidoMaterno());
        m.put("estadoCivil", d.estadoCivil());
        m.put("email", d.email());
        m.put("fechaNacimiento", d.fechaNacimiento());
        m.put("departamento", d.departamento());
        m.put("provincia", d.provincia());
        m.put("distrito", d.distrito());
        m.put("direccion", d.direccion());
        m.put("ubicacionGPSCasa", d.ubicacionGPSCasa());
        m.put("telefono1", d.telefono1());
        m.put("telefono2", d.telefono2());
        m.put("ocupacion", d.ocupacion());
        m.put("rangoIngresos", d.rangoIngresos());
        m.put("tipoVivienda", d.tipoVivienda());
        m.put("licenciaConducir", d.licenciaConducir());
        m.put("numeroLicencia", d.numeroLicencia());
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }
}
