package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.VerificarReferenciaCommand;
import com.motoyav2.evaluacion.domain.model.Referencia;
import com.motoyav2.evaluacion.domain.port.in.VerificarReferenciaUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VerificarReferenciaUseCaseImpl implements VerificarReferenciaUseCase {

    private final ReferenciaRepository referenciaRepository;

    @Override
    public Mono<Referencia> ejecutar(VerificarReferenciaCommand command) {
        return referenciaRepository.findById(command.referenciaId())
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Referencia no encontrada: " + command.referenciaId())))
                .flatMap(referencia -> {
                    Timestamp ahora = Timestamp.now();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estadoVerificacion", command.estadoVerificacion());
                    updates.put("resultadoContacto", command.resultadoContacto());
                    updates.put("scoreVerificacion", command.scoreVerificacion());
                    updates.put("observaciones", command.observaciones());
                    updates.put("actitudDuranteContacto", command.actitudDuranteContacto());
                    updates.put("fechaContacto", ahora);
                    updates.put("updatedAt", ahora);

                    return referenciaRepository.updateFields(command.referenciaId(), updates)
                            .then(referenciaRepository.findById(command.referenciaId()));
                });
    }
}
