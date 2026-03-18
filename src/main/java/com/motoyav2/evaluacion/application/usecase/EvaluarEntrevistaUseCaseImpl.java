package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.EvaluarEntrevistaCommand;
import com.motoyav2.evaluacion.domain.port.in.EvaluarEntrevistaUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvaluarEntrevistaUseCaseImpl implements EvaluarEntrevistaUseCase {

    private final ClienteRepository clienteRepository;
    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<Void> ejecutar(EvaluarEntrevistaCommand cmd) {
        return clienteRepository.findById(cmd.clienteId())
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException(
                        "Cliente no encontrado: " + cmd.clienteId())))
                .flatMap(cliente -> {
                    Timestamp ahora = Timestamp.now();

                    // Preserve createdAt if entrevista already exists
                    Timestamp createdAt = (cliente.getEvaluacionEntrevista() != null
                            && cliente.getEvaluacionEntrevista().getCreatedAt() != null)
                            ? cliente.getEvaluacionEntrevista().getCreatedAt()
                            : ahora;

                    Map<String, Object> entrevistaMap = buildEntrevistaMap(cmd, createdAt, ahora);

                    Map<String, Object> clienteUpdates = new HashMap<>();
                    clienteUpdates.put("evaluacionEntrevista", entrevistaMap);
                    clienteUpdates.put("updatedAt", ahora);

                    Mono<Void> saveCliente = clienteRepository.updateFields(cmd.clienteId(), clienteUpdates);

                    // Also propagate scoreEntrevista to solicitud for real-time display
                    if (cmd.scoreEntrevista() != null && cmd.solicitudId() != null) {
                        Map<String, Object> solicitudUpdates = new HashMap<>();
                        solicitudUpdates.put("scoreEntrevista", cmd.scoreEntrevista().doubleValue());
                        solicitudUpdates.put("updatedAt", ahora);
                        return saveCliente.then(
                                solicitudRepository.updateFields(cmd.solicitudId(), solicitudUpdates));
                    }
                    return saveCliente;
                });
    }

    private Map<String, Object> buildEntrevistaMap(EvaluarEntrevistaCommand cmd,
                                                    Timestamp createdAt,
                                                    Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("solicitudId",              cmd.solicitudId());
        m.put("entrevistadorId",          cmd.usuarioId());
        m.put("entrevistadorNombre",      cmd.usuarioNombre());
        m.put("modalidad",                cmd.modalidad());
        m.put("plataforma",               cmd.plataforma());
        m.put("puntualidad",              cmd.puntualidad());
        m.put("presentacionPersonal",     cmd.presentacionPersonal());
        m.put("actitudColaboracion",      cmd.actitudColaboracion());
        m.put("coherenciaRespuestas",     cmd.coherenciaRespuestas());
        m.put("nivelConfianza",           cmd.nivelConfianza());
        m.put("scoreEntrevista",          cmd.scoreEntrevista());
        m.put("observacionesCliente",     cmd.observacionesCliente());
        m.put("observacionesFiador",      cmd.observacionesFiador());
        m.put("observacionesDomicilio",   cmd.observacionesDomicilio());
        m.put("observacionesCapacidadPago", cmd.observacionesCapacidadPago());
        m.put("hallazgosPositivos",       cmd.hallazgosPositivos() != null ? cmd.hallazgosPositivos() : List.of());
        m.put("hallazgosNegativos",       cmd.hallazgosNegativos() != null ? cmd.hallazgosNegativos() : List.of());
        m.put("alertas",                  List.of());
        m.put("recomendacion",            cmd.recomendacion());
        m.put("motivoRecomendacion",      cmd.motivoRecomendacion());
        m.put("condiciones",              cmd.condiciones() != null ? cmd.condiciones() : List.of());
        m.put("esBorrador",               Boolean.TRUE.equals(cmd.esBorrador()));
        m.put("createdAt",                createdAt);
        m.put("updatedAt",                ahora);
        return m;
    }
}
