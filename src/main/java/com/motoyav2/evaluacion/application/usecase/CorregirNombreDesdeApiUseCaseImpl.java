package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.CorregirNombreDesdeApiUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
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
public class CorregirNombreDesdeApiUseCaseImpl implements CorregirNombreDesdeApiUseCase {

    private final ClienteRepository clienteRepository;

    @Override
    public Mono<Void> ejecutar(String clienteId) {
        return clienteRepository.findById(clienteId)
                .switchIfEmpty(Mono.error(
                        new RecursoNoEncontradoException("Cliente no encontrado: " + clienteId)))
                .flatMap(cliente -> {
                    var snap = cliente.getVerificacionIdentidad();

                    if (snap == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "No existe verificación de identidad previa para este cliente"));
                    }
                    if (!snap.exitoso()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "La última verificación no fue exitosa; no hay datos de la API para aplicar"));
                    }
                    if (snap.apiNombres() == null && snap.apiApellidoPaterno() == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "La API no devolvió nombres para este cliente"));
                    }

                    Map<String, Object> updates = new HashMap<>();
                    if (snap.apiNombres() != null)         updates.put("nombres",          snap.apiNombres());
                    if (snap.apiApellidoPaterno() != null) updates.put("apellidoPaterno",   snap.apiApellidoPaterno());
                    if (snap.apiApellidoMaterno() != null) updates.put("apellidoMaterno",   snap.apiApellidoMaterno());
                    updates.put("updatedAt", Timestamp.now());

                    return clienteRepository.updateFields(clienteId, updates)
                            .doOnSuccess(v -> log.info(
                                    "Nombre corregido desde API — cliente={} nombres='{}' apPaterno='{}' apMaterno='{}'",
                                    clienteId, snap.apiNombres(), snap.apiApellidoPaterno(), snap.apiApellidoMaterno()));
                });
    }
}
