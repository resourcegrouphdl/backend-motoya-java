package com.motoyav2.alertascenter.infrastructure.scheduler;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.alertascenter.application.service.FcmPushService;
import com.motoyav2.alertascenter.domain.model.EstadoAlerta;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * Re-envía push FCM cada 5 minutos mientras haya alertas en estado PENDING.
 * Solo re-notifica alertas con más de 4 minutos de antigüedad para no duplicar
 * el push inicial que ya envió AlertaCenterService al crearlas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertaReminderScheduler {

    private final Firestore firestore;
    private final FcmPushService fcmPushService;

    @Value("${alertas.collections.alertas:alertas_internas}")
    private String alertasCollection;

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000) // cada 5 min, empieza tras 5 min
    public void recordarAlertasPendientes() {
        // Solo alertas creadas hace más de 4 min (evita duplicar el push de creación)
        Instant cutoff = Instant.now().minus(4, ChronoUnit.MINUTES);
        Timestamp cutoffTs = Timestamp.of(Date.from(cutoff));

        FirestoreUtils.toFlux(
                firestore.collection(alertasCollection)
                        .whereEqualTo("estado", EstadoAlerta.PENDING.name())
                        .whereLessThan("creadoEn", cutoffTs)
                        .get()
        )
        .collectList()
        .flatMap(docs -> {
            if (docs.isEmpty()) {
                log.debug("Reminder: sin alertas PENDING pendientes de atención");
                return reactor.core.publisher.Mono.empty();
            }

            int n = docs.size();
            log.info("Reminder: {} alerta(s) PENDING sin atender — re-enviando push", n);

            String titulo = n == 1
                    ? "\u26a0\ufe0f Alerta sin atender"
                    : "\u26a0\ufe0f " + n + " alertas sin atender";
            String mensaje = n == 1
                    ? "Hay una solicitud pendiente que requiere atención inmediata."
                    : "Hay " + n + " solicitudes pendientes que requieren atención.";

            Map<String, String> data = Map.of(
                    "tipo", "REMINDER",
                    "pendientes", String.valueOf(n)
            );

            return fcmPushService.enviarATodos(titulo, mensaje, data);
        })
        .subscribe(
                null,
                e -> log.error("Error en reminder de alertas PENDING: {}", e.getMessage())
        );
    }
}
