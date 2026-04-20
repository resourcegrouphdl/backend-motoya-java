package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.model.SemaforoReferencias;
import com.motoyav2.evaluacion.domain.port.in.ActualizarSemaforoReferenciasUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.service.CalculadoraSemaforoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualizarSemaforoReferenciasUseCaseImpl implements ActualizarSemaforoReferenciasUseCase {

    private final SolicitudRepository   solicitudRepository;
    private final ReferenciaRepository  referenciaRepository;

    @Override
    public Mono<Void> actualizar(String solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .flatMap(solicitud -> {
                    if (solicitud.getReferenciasIds() == null || solicitud.getReferenciasIds().isEmpty()) {
                        return Mono.empty();
                    }
                    return referenciaRepository.findByIds(solicitud.getReferenciasIds())
                            .collectList()
                            .flatMap(refs -> {
                                SemaforoReferencias semaforo = CalculadoraSemaforoService.calcular(refs);
                                log.info("[SEMAFORO] solicitud={} semaforo={} (verificadas={}/{})",
                                        solicitudId, semaforo,
                                        refs.stream().filter(r -> "verificado".equals(r.getEstadoVerificacion())).count(),
                                        refs.size());
                                return solicitudRepository.updateFields(solicitudId, Map.of(
                                        "semaforoReferencias", semaforo.toFirestoreValue(),
                                        "updatedAt", Timestamp.now()
                                ));
                            });
                })
                .onErrorResume(e -> {
                    log.error("[SEMAFORO] Error actualizando semáforo solicitud={}: {}", solicitudId, e.getMessage());
                    return Mono.empty();
                });
    }
}
