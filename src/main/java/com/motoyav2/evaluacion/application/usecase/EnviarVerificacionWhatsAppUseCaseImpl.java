package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.EnviarVerificacionWhatsAppUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
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
public class EnviarVerificacionWhatsAppUseCaseImpl implements EnviarVerificacionWhatsAppUseCase {

    private final ReferenciaRepository    referenciaRepository;
    private final SolicitudRepository     solicitudRepository;
    private final FactilizaWhatsAppNotificationAdapter whatsApp;

    @Override
    public Mono<Void> ejecutar(String referenciaId, String solicitudId) {
        return referenciaRepository.findById(referenciaId)
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Referencia no encontrada: " + referenciaId)))
                .zipWith(
                        solicitudRepository.findById(solicitudId)
                                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Solicitud no encontrada: " + solicitudId)))
                )
                .flatMap(tuple -> {
                    var ref      = tuple.getT1();
                    var solicitud = tuple.getT2();

                    String telefonoRef  = ref.getTelefono();
                    String nombreRef    = (ref.getNombre() != null ? ref.getNombre() : "") + " " +
                                         (ref.getApellidos() != null ? ref.getApellidos() : "");
                    String titularNombre = solicitud.getTitularNombreCompleto() != null
                            ? solicitud.getTitularNombreCompleto()
                            : "el/la solicitante";

                    String mensaje = buildMensaje(nombreRef.trim(), titularNombre);

                    return whatsApp.sendText(telefonoRef, mensaje)
                            .flatMap(wamid -> {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("wamid",               wamid);
                                updates.put("estadoVerificacion",  "wa_enviado");
                                updates.put("fechaEnvioWhatsapp",  Timestamp.now());
                                updates.put("solicitudId",         solicitudId);
                                updates.put("metodoVerificacion",  "automatico");
                                updates.put("updatedAt",           Timestamp.now());
                                return referenciaRepository.updateFields(referenciaId, updates);
                            })
                            .doOnSuccess(v -> log.info("[WA-VERIF] Enviado a referencia={} telefono={}", referenciaId, telefonoRef))
                            .doOnError(e -> log.error("[WA-VERIF] Error enviando a referencia={}: {}", referenciaId, e.getMessage()));
                });
    }

    private String buildMensaje(String nombreRef, String titular) {
        return """
                Hola %s 👋

                Somos *Motoya Digital*, empresa de financiamiento de motocicletas en Perú.

                *%s* ha indicado tu nombre como referencia personal para una solicitud de crédito.

                ¿Confirmas conocer a esta persona y tener buena referencia de ella?

                Por favor responde únicamente:
                ✅ *SÍ* — si la conoces y la refieres positivamente
                ❌ *NO* — si no la conoces o no deseas ser referencia

                Gracias por tu tiempo.
                _Motoya Digital_""".formatted(nombreRef, titular);
    }
}
