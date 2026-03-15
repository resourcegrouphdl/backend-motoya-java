package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.motoyav2.contrato.domain.port.out.StoragePort;
import com.motoyav2.notifications.domain.events.BusinessEventType;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.model.NotificationRequest;
import com.motoyav2.notifications.domain.ports.in.PublishBusinessEventUseCase;
import com.motoyav2.notifications.domain.ports.in.SendNotificationUseCase;
import com.motoyav2.notifications.infrastructure.adapter.in.web.dto.SendEmailRequest;
import com.motoyav2.notifications.infrastructure.adapter.in.web.dto.SendMediaRequest;
import com.motoyav2.notifications.infrastructure.adapter.in.web.dto.SendNotificationRequest;
import com.motoyav2.notifications.infrastructure.adapter.in.web.dto.SendNotificationResponse;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Endpoint público de notificaciones.
 * Protegido por Firebase Auth (aplica el filtro global de la app).
 *
 * POST /api/v1/notifications/send        → envío síncrono (cualquier canal)
 * POST /api/v1/notifications/async       → envío asíncrono vía Outbox (recomendado)
 * POST /api/v1/notifications/email       → email síncrono
 * POST /api/v1/notifications/email/async → email asíncrono vía Outbox
 * POST /api/v1/notifications/send/media  → envío de archivo por WhatsApp (Meta API)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones", description = "Envío de notificaciones por WhatsApp (Meta) y Email")
public class NotificationController {

    private final SendNotificationUseCase sendNotificationUseCase;
    private final PublishBusinessEventUseCase publishBusinessEventUseCase;
    private final MetaWhatsAppNotificationAdapter metaAdapter;
    private final StoragePort storagePort;

    /**
     * Envío SÍNCRONO: llama al canal directamente y espera la respuesta.
     */
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Enviar notificación directa",
            description = "Envía la notificación de forma síncrona. " +
                    "Retorna cuando el envío se completa o falla.")
    public Mono<SendNotificationResponse> sendDirect(
            @Valid @RequestBody SendNotificationRequest req) {

        log.info("[NOTIF-API] Solicitud directa | canal={} plantilla={} destinatario={}",
                req.channel(), req.template(), req.recipient());

        NotificationRequest domainRequest = NotificationRequest.builder()
                .channel(req.channel())
                .recipient(req.recipient())
                .template(req.template())
                .variables(req.variables())
                .build();

        return sendNotificationUseCase.send(domainRequest)
                .thenReturn(SendNotificationResponse.direct())
                .doOnSuccess(r -> log.info("[NOTIF-API] ✓ Enviado directamente | canal={} destinatario={}",
                        req.channel(), req.recipient()));
    }

    /**
     * Envío ASÍNCRONO vía Outbox: guarda el evento en Firestore y retorna inmediatamente.
     * El trigger de Cloud Function procesará el envío en background con reintentos automáticos.
     */
    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Encolar notificación (asíncrono)",
            description = "Guarda el evento en el Outbox y retorna 202 inmediatamente.")
    public Mono<SendNotificationResponse> sendAsync(
            @Valid @RequestBody SendNotificationRequest req) {

        log.info("[NOTIF-API] Solicitud async | canal={} plantilla={} destinatario={}",
                req.channel(), req.template(), req.recipient());

        return publishBusinessEventUseCase.publish(
                        BusinessEventType.MANUAL,
                        req.contratoId(),
                        req.channel(),
                        req.recipient(),
                        req.template(),
                        req.variables()
                )
                .thenReturn(SendNotificationResponse.async(null))
                .doOnSuccess(r -> log.info("[NOTIF-API] ✓ Encolado | canal={} destinatario={}",
                        req.channel(), req.recipient()));
    }

    // ─── Email dedicado ───────────────────────────────────────────────────────

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Enviar email directo",
            description = "Envía un correo electrónico de forma síncrona usando una plantilla HTML.")
    public Mono<SendNotificationResponse> sendEmail(
            @Valid @RequestBody SendEmailRequest req) {

        log.info("[NOTIF-API] Email directo | plantilla={} to={}", req.template(), req.to());

        NotificationRequest domainRequest = NotificationRequest.builder()
                .channel(NotificationChannel.EMAIL)
                .recipient(req.to())
                .template(req.template())
                .variables(req.variables())
                .build();

        return sendNotificationUseCase.send(domainRequest)
                .thenReturn(SendNotificationResponse.direct())
                .doOnSuccess(r -> log.info("[NOTIF-API] ✓ Email enviado | to={}", req.to()));
    }

    @PostMapping("/email/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Encolar email (asíncrono)",
            description = "Guarda el evento de email en el Outbox y retorna 202 inmediatamente.")
    public Mono<SendNotificationResponse> sendEmailAsync(
            @Valid @RequestBody SendEmailRequest req) {

        log.info("[NOTIF-API] Email async | plantilla={} to={}", req.template(), req.to());

        return publishBusinessEventUseCase.publish(
                        BusinessEventType.MANUAL,
                        req.contratoId(),
                        NotificationChannel.EMAIL,
                        req.to(),
                        req.template(),
                        req.variables()
                )
                .thenReturn(SendNotificationResponse.async(null))
                .doOnSuccess(r -> log.info("[NOTIF-API] ✓ Email encolado | to={}", req.to()));
    }

    // ─── WhatsApp Media (Meta API) ────────────────────────────────────────────

    /**
     * Envía un archivo (imagen, documento, video, audio) por WhatsApp vía Meta API.
     * El archivo debe estar previamente subido en Firebase Storage.
     * Se obtiene la URL de descarga automáticamente.
     */
    @PostMapping("/send/media")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Enviar archivo por WhatsApp",
            description = "Obtiene la URL de Firebase Storage y envía el archivo por WhatsApp usando Meta API. " +
                    "mediaType válidos: image, document, video, audio")
    public Mono<SendNotificationResponse> sendMedia(
            @Valid @RequestBody SendMediaRequest req) {

        log.info("[NOTIF-API] Media WhatsApp | to={} path={} tipo={}",
                req.recipient(), req.storagePath(), req.mediaType());

        return storagePort.getDownloadUrl(req.storagePath())
                .flatMap(url -> metaAdapter.sendMedia(
                        req.recipient(),
                        url,
                        req.mediaType(),
                        req.filename(),
                        req.caption()))
                .thenReturn(SendNotificationResponse.direct())
                .doOnSuccess(r -> log.info("[NOTIF-API] ✓ Media enviado | to={}", req.recipient()));
    }
}
