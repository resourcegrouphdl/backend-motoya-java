package com.motoyav2.riesgointerno.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.riesgointerno.domain.enums.EstadoRegistro;
import com.motoyav2.riesgointerno.domain.model.HistorialCambioRiesgo;
import com.motoyav2.riesgointerno.domain.port.in.CambiarEstadoRiesgoUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CambiarEstadoRiesgoUseCaseImpl implements CambiarEstadoRiesgoUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Mono<Void> cambiarEstado(String id, EstadoRegistro nuevoEstado, String motivo, String uid) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Registro de riesgo no encontrado: " + id)))
                .flatMap(registro -> {
                    Timestamp ahora = Timestamp.now();
                    var entrada = HistorialCambioRiesgo.builder()
                            .fecha(ahora)
                            .usuario(uid)
                            .cambio("estadoRegistro: " + registro.getEstadoRegistro() + " → " + nuevoEstado)
                            .motivoCambio(motivo != null ? motivo : "")
                            .build();

                    var historial = new ArrayList<>(registro.getHistorialCambios() != null
                            ? registro.getHistorialCambios() : java.util.List.of());
                    historial.add(entrada);

                    Map<String, Object> fields = new HashMap<>();
                    fields.put("estadoRegistro", nuevoEstado.name());
                    fields.put("historialCambios", historial.stream()
                            .map(h -> Map.of(
                                    "fecha", h.getFecha(),
                                    "usuario", h.getUsuario(),
                                    "cambio", h.getCambio(),
                                    "motivoCambio", h.getMotivoCambio()
                            )).toList());
                    fields.put("updatedAt", ahora);

                    return repository.updateFields(id, fields);
                });
    }
}
