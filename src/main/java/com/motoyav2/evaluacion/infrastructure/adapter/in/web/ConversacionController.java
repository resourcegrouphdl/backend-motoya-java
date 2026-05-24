package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.google.cloud.Timestamp;
import com.motoyav2.notifications.domain.model.conversacion.ConversacionWa;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.MensajeWa;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.notifications.domain.port.out.ConversacionRepository;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import com.motoyav2.shared.security.FirebaseUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/evaluacion")
@RequiredArgsConstructor
@Tag(name = "Conversaciones WhatsApp", description = "Historial de conversaciones WhatsApp por solicitud")
public class ConversacionController {

    private final ConversacionRepository                conversacionRepository;
    private final RegistrarMensajeConversacionUseCase   registrarMensaje;
    private final MetaWhatsAppNotificationAdapter       whatsApp;

    // ── [1] Listar conversaciones de una solicitud ────────────────────────────

    @GetMapping("/{solicitudId}/conversaciones")
    @Operation(summary = "Obtener historial de conversaciones WhatsApp para una solicitud")
    public Flux<ConversacionWaDto> getConversaciones(@PathVariable String solicitudId) {
        return conversacionRepository.findBySolicitudId(solicitudId)
                .map(ConversacionController::toDto);
    }

    // ── [2] Enviar mensaje manual desde el evaluador ──────────────────────────

    @PostMapping("/{solicitudId}/conversaciones/{telefono}/mensaje")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Enviar mensaje WhatsApp manual desde el panel del evaluador")
    public Mono<Map<String, String>> enviarMensaje(
            @PathVariable String solicitudId,
            @PathVariable String telefono,
            @RequestBody EnviarMensajeRequest req,
            @AuthenticationPrincipal FirebaseUserDetails user) {

        if (req.texto() == null || req.texto().isBlank()) {
            return Mono.just(Map.of("status", "error", "message", "El texto no puede estar vacío"));
        }

        String evaluadorNombre = user != null ? user.email() : "Evaluador";
        RolParticipante rol    = req.rol() != null ? parseRol(req.rol()) : RolParticipante.TITULAR;

        return whatsApp.sendText(telefono, req.texto())
                .flatMap(wamid -> registrarMensaje.registrar(
                        solicitudId, telefono, req.nombreParticipante(), rol,
                        DireccionMensaje.OUTBOUND, TipoMensajeWa.TEXTO,
                        req.texto(), null, evaluadorNombre, null, null))
                .thenReturn(Map.of("status", "sent"))
                .doOnSuccess(v -> log.info("[CONV-CTRL] Mensaje manual enviado | solicitud={} evaluador={}", solicitudId, evaluadorNombre))
                .onErrorResume(e -> {
                    log.error("[CONV-CTRL] Error enviando mensaje manual: {}", e.getMessage());
                    return Mono.just(Map.of("status", "error", "message", e.getMessage()));
                });
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record EnviarMensajeRequest(String texto, String nombreParticipante, String rol) {}

    public record ConversacionWaDto(
            String id,
            String solicitudId,
            String telefono,
            String nombreParticipante,
            String rol,
            String estadoConversacion,
            List<MensajeWaDto> mensajes,
            String ultimaActividad
    ) {}

    public record MensajeWaDto(
            String id,
            String direccion,
            String tipo,
            String contenido,
            String mediaUrl,
            String enviadorNombre,
            String claudeClasificacion,
            Double claudeConfianza,
            String timestamp
    ) {}

    // ── Mappers ───────────────────────────────────────────────────────────────

    private static ConversacionWaDto toDto(ConversacionWa c) {
        List<MensajeWaDto> mensajes = c.mensajes() != null
                ? c.mensajes().stream().map(ConversacionController::toMsgDto).toList()
                : List.of();
        return new ConversacionWaDto(
                c.id(), c.solicitudId(), c.telefono(), c.nombreParticipante(),
                c.rol() != null ? c.rol().name() : null,
                c.estadoConversacion() != null ? c.estadoConversacion().name() : null,
                mensajes,
                c.ultimaActividad() != null ? c.ultimaActividad().toString() : null
        );
    }

    private static MensajeWaDto toMsgDto(MensajeWa m) {
        return new MensajeWaDto(
                m.id(),
                m.direccion() != null ? m.direccion().name() : null,
                m.tipo() != null ? m.tipo().name() : null,
                m.contenido(),
                m.mediaUrl(),
                m.enviadorNombre(),
                m.claudeClasificacion(),
                m.claudeConfianza(),
                m.timestamp() != null ? m.timestamp().toString() : null
        );
    }

    private RolParticipante parseRol(String rol) {
        try { return RolParticipante.valueOf(rol.toUpperCase()); }
        catch (Exception e) { return RolParticipante.TITULAR; }
    }
}
