package com.motoyav2.notifications.application.usecase;

import com.motoyav2.notifications.domain.model.conversacion.*;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.notifications.domain.port.out.ConversacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrarMensajeConversacionUseCaseImpl implements RegistrarMensajeConversacionUseCase {

    private final ConversacionRepository conversacionRepository;

    @Override
    public Mono<Void> registrar(
            String solicitudId,
            String telefono,
            String nombreParticipante,
            RolParticipante rol,
            DireccionMensaje direccion,
            TipoMensajeWa tipo,
            String contenido,
            String mediaUrl,
            String enviadorNombre,
            String claudeClasificacion,
            Double claudeConfianza) {

        String convId = ConversacionRepository.buildId(solicitudId, rol, telefono);
        Instant ahora = Instant.now();

        ConversacionWa convHeader = new ConversacionWa(
                convId, solicitudId, normalizePhone(telefono),
                nombreParticipante, rol,
                EstadoConversacion.ESPERANDO_RESPUESTA,
                Collections.emptyList(), ahora, ahora
        );

        MensajeWa mensaje = new MensajeWa(
                UUID.randomUUID().toString(),
                direccion, tipo, contenido, mediaUrl, null,
                enviadorNombre, claudeClasificacion, claudeConfianza,
                ahora
        );

        return conversacionRepository.upsertConversacion(convHeader)
                .then(conversacionRepository.agregarMensaje(convId, mensaje))
                .doOnSuccess(v -> log.debug("[CONV] Mensaje registrado | conv={} dir={} tipo={}",
                        convId, direccion, tipo))
                .onErrorResume(e -> {
                    log.error("[CONV] Error registrando mensaje para solicitud={}: {}", solicitudId, e.getMessage());
                    return Mono.empty();
                });
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        return digits;
    }
}
