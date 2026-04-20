package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.EnviarBienvenidaWhatsAppUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.FactilizaWhatsAppNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnviarBienvenidaWhatsAppUseCaseImpl implements EnviarBienvenidaWhatsAppUseCase {

    private final FactilizaWhatsAppNotificationAdapter whatsApp;
    private final RegistrarMensajeConversacionUseCase  registrarMensaje;
    private final SolicitudRepository                  solicitudRepository;

    @Override
    public Mono<Void> enviar(String solicitudId, String telefono, String nombre, boolean esFiador) {
        if (telefono == null || telefono.isBlank()) return Mono.empty();

        String mensaje  = buildMensaje(nombre, esFiador);
        RolParticipante rol = esFiador ? RolParticipante.FIADOR : RolParticipante.TITULAR;

        return whatsApp.sendText(telefono, mensaje)
                .flatMap(wamid -> {
                    // Marcar en solicitud que se envió bienvenida
                    Map<String, Object> updates = new HashMap<>();
                    String campo = esFiador ? "entrevista.fiadorBienvenidaEnviada" : "entrevista.titularBienvenidaEnviada";
                    updates.put(campo, true);
                    updates.put("entrevista.estadoConversacion", "ESPERANDO_PREFERENCIA");
                    updates.put("updatedAt", Timestamp.now());
                    return solicitudRepository.updateFields(solicitudId, updates)
                            .then(registrarMensaje.registrar(
                                    solicitudId, telefono, nombre, rol,
                                    DireccionMensaje.OUTBOUND, TipoMensajeWa.TEXTO,
                                    mensaje, null, "Sistema Automático",
                                    null, null));
                })
                .doOnSuccess(v -> log.info("[BIENVENIDA] Enviado a {}={} solicitud={}", rol, telefono, solicitudId))
                .onErrorResume(e -> {
                    log.warn("[BIENVENIDA] Error enviando bienvenida a telefono={} solicitud={}: {}", telefono, solicitudId, e.getMessage());
                    return Mono.empty();
                });
    }

    private String buildMensaje(String nombre, boolean esFiador) {
        String primerNombre = nombre != null && nombre.contains(" ")
                ? nombre.split(" ")[0] : (nombre != null ? nombre : "");

        if (esFiador) {
            return """
                    Hola %s 👋

                    Somos *Motoya Digital*, empresa de financiamiento de motocicletas.

                    Has sido registrado como garante en una solicitud de crédito. En breve, un asesor de *Motoya Digital* se comunicará contigo para una breve entrevista de verificación.

                    ¿Tienes preferencia de horario? Si lo deseas, indícanos:
                    📅 *hoy* o *mañana* y la hora (ej: "mañana a las 3pm")

                    Si no tienes preferencia, no es necesario que respondas.

                    _Motoya Digital_""".formatted(primerNombre);
        } else {
            return """
                    Hola %s 👋

                    Somos *Motoya Digital*, empresa de financiamiento de motocicletas.

                    Hemos recibido correctamente tu solicitud de crédito. En breve, un asesor de *Motoya Digital* se comunicará contigo para una breve entrevista.

                    ¿Tienes preferencia de horario para la entrevista? Si lo deseas, indícanos:
                    📅 *hoy* o *mañana* y la hora (ej: "mañana a las 3pm")

                    Si no tienes preferencia, no es necesario que respondas.

                    _Motoya Digital_""".formatted(primerNombre);
        }
    }
}
