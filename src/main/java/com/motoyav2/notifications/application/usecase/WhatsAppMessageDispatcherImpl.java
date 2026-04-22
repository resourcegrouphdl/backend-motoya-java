package com.motoyav2.notifications.application.usecase;

import com.motoyav2.cobranza.application.port.in.ProcesarVoucherWhatsappUseCase;
import com.motoyav2.cobranza.application.service.WhatsappService;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.CasoCobranzaRepository;
import com.motoyav2.evaluacion.domain.port.in.ProcesarPreferenciaEntrevistaUseCase;
import com.motoyav2.evaluacion.domain.port.in.ProcesarRespuestaReferenciaUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;

/**
 * Enrutador central de mensajes entrantes de WhatsApp.
 *
 * Orden de resolución de contexto:
 *   1. ¿Es una referencia activa (wa_enviado)?  → ProcesarRespuestaReferencia
 *   2. ¿Es el titular de una solicitud activa?  → ProcesarPreferenciaEntrevista
 *   3. ¿Es el fiador de una solicitud activa?   → ProcesarPreferenciaEntrevista
 *   4. Desconocido                              → log + ignorar
 *
 * Media (imagen/PDF): se registra en la conversación si se pudo resolver el contexto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMessageDispatcherImpl implements WhatsAppMessageDispatcher {

    private static final List<String> CICLOS_INACTIVOS =
            List.of("PAGADO_TOTAL", "JUDICIAL", "CASTIGADO", "CERRADO");

    private final ReferenciaRepository                 referenciaRepository;
    private final SolicitudRepository                  solicitudRepository;
    private final CasoCobranzaRepository               casoCobranzaRepository;
    private final ProcesarRespuestaReferenciaUseCase   procesarReferencia;
    private final ProcesarPreferenciaEntrevistaUseCase procesarPreferencia;
    private final ProcesarVoucherWhatsappUseCase       procesarVoucherWhatsapp;
    private final RegistrarMensajeConversacionUseCase  registrarMensaje;
    private final NotificationFacade                   notificationFacade;
    private final WhatsappService                      whatsappService;

    @Override
    public Mono<Void> dispatch(String fromPhone, String text, String mediaType, String mediaUrl) {
        String phone = normalizePhone(fromPhone);
        log.info("[DISPATCHER] Mensaje recibido | fromRaw={} phoneNormalizado={} text={}", fromPhone, phone, text);
        if (phone.isBlank()) return Mono.empty();

        // ── 1. Intentar resolver como referencia ──────────────────────────────
        return referenciaRepository.findByTelefonoAndEstadoWaEnviado(phone)
                .flatMap(ref -> {
                    log.info("[DISPATCHER] Mensaje de REFERENCIA | refId={} phone={}", ref.getId(), phone);
                    if (text != null) {
                        // Registrar inbound en conversación (la referencia tiene su propia lógica)
                        String nombre = (ref.getNombre() != null ? ref.getNombre() : "") + " " +
                                (ref.getApellidos() != null ? ref.getApellidos() : "");
                        registrarMensaje.registrar(
                                ref.getSolicitudId(), phone, nombre.trim(),
                                RolParticipante.REFERENCIA,
                                DireccionMensaje.INBOUND, TipoMensajeWa.TEXTO,
                                text, null, null, null, null
                        ).subscribe(null, e -> log.warn("[DISPATCHER] Error registrando msg referencia: {}", e.getMessage()));

                        return procesarReferencia.ejecutar(phone, text);
                    }
                    return handleMedia(ref.getSolicitudId(), phone, ref.getNombre(), RolParticipante.REFERENCIA, mediaType, mediaUrl);
                })
                // ── 2. Intentar resolver como titular ─────────────────────────
                .switchIfEmpty(solicitudRepository.findActivaByTitularTelefono(phone)
                        .flatMap(sol -> {
                            log.info("[DISPATCHER] Mensaje de TITULAR | solicitudId={} phone={}", sol.getId(), phone);
                            if (text != null) {
                                return procesarPreferencia.procesar(sol.getId(), phone, text, false);
                            }
                            return handleMedia(sol.getId(), phone, sol.getTitularNombreCompleto(), RolParticipante.TITULAR, mediaType, mediaUrl);
                        }))
                // ── 3. Intentar resolver como fiador ──────────────────────────
                .switchIfEmpty(solicitudRepository.findActivaByFiadorTelefono(phone)
                        .flatMap(sol -> {
                            log.info("[DISPATCHER] Mensaje de FIADOR | solicitudId={} phone={}", sol.getId(), phone);
                            if (text != null) {
                                return procesarPreferencia.procesar(sol.getId(), phone, text, true);
                            }
                            return handleMedia(sol.getId(), phone, "Fiador", RolParticipante.FIADOR, mediaType, mediaUrl);
                        }))
                // ── 4. Intentar resolver como cliente de cobranza ────────────
                .switchIfEmpty(buscarCasoActivoPorTelefono(phone)
                        .flatMap(caso -> {
                            log.info("[DISPATCHER] Mensaje de COBRANZA | contratoId={} phone={}",
                                    caso.getContratoId(), phone);
                            if (text != null) {
                                // Guardar mensaje de texto entrante en historial
                                whatsappService.registrarMensajeEntrante(
                                        caso.getContratoId(), phone, null, text, new Date())
                                    .subscribe(null, e -> log.warn("[DISPATCHER] Error guardando texto cobranza: {}", e.getMessage()));
                                String nombre = caso.getClienteNombre() != null
                                        ? caso.getClienteNombre() : "Cliente";
                                return notificationFacade.notificarAutorespuestaCobranza(
                                        caso.getContratoId(), phone, nombre);
                            }
                            // Media (imagen/PDF): guardar en historial + procesar como comprobante
                            if (mediaUrl != null) {
                                whatsappService.registrarMediaEntrante(
                                        caso.getContratoId(), caso.getClienteNombre(), phone,
                                        mediaUrl, mediaType, new Date())
                                    .subscribe(null, e -> log.warn("[DISPATCHER] Error guardando media cobranza: {}", e.getMessage()));
                                return procesarVoucherWhatsapp.procesar(
                                        caso.getContratoId(),
                                        caso.getStoreId(),
                                        caso.getClienteNombre(),
                                        phone,
                                        mediaUrl,
                                        mediaType);
                            }
                            return Mono.empty();
                        }))
                // ── 5. Sin contexto ───────────────────────────────────────────
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("[DISPATCHER] Sin contexto para phone={} — no es referencia, titular, fiador ni cliente de cobranza activo", phone)))
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("index")) {
                        log.error("[DISPATCHER] *** ÍNDICE FIRESTORE FALTANTE *** phone={}: {}", phone, e.getMessage());
                    } else {
                        log.error("[DISPATCHER] Error procesando mensaje phone={}: {}", phone, e.getMessage());
                    }
                    return Mono.empty();
                });
    }

    /**
     * Busca el caso de cobranza activo por teléfono del cliente.
     * clienteTelefono se almacena como 9 dígitos (sin +51), el phone normalizado
     * viene como +51XXXXXXXXX — se convierte antes de consultar.
     */
    private Mono<CasoCobranzaDocument> buscarCasoActivoPorTelefono(String normalizedPhone) {
        String telefono9 = toCasoTelefono(normalizedPhone);
        if (telefono9.isBlank()) return Mono.empty();
        return casoCobranzaRepository.findByClienteTelefono(telefono9)
                .filter(c -> c.getCicloVida() == null || !CICLOS_INACTIVOS.contains(c.getCicloVida()))
                .next();
    }

    /** Convierte +51XXXXXXXXX → XXXXXXXXX (9 dígitos, como se almacena en cobranzas-casos). */
    private String toCasoTelefono(String normalizedPhone) {
        if (normalizedPhone == null) return "";
        String digits = normalizedPhone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        if (digits.length() == 9) return digits;
        return "";
    }

    private Mono<Void> handleMedia(String solicitudId, String phone, String nombre,
                                    RolParticipante rol, String mediaType, String mediaUrl) {
        if (mediaUrl == null) return Mono.empty();
        TipoMensajeWa tipo = detectarTipo(mediaType);
        String contenido = tipo.name() + " recibido" + (mediaType != null ? " (" + mediaType + ")" : "");
        log.info("[DISPATCHER] Media recibida | solicitudId={} tipo={} phone={}", solicitudId, tipo, phone);
        return registrarMensaje.registrar(
                solicitudId, phone, nombre != null ? nombre : "Participante",
                rol, DireccionMensaje.INBOUND, tipo,
                contenido, mediaUrl, null, null, null);
    }

    private TipoMensajeWa detectarTipo(String mediaType) {
        if (mediaType == null) return TipoMensajeWa.DESCONOCIDO;
        return switch (mediaType.toLowerCase()) {
            case "image"    -> TipoMensajeWa.IMAGEN;
            case "document" -> TipoMensajeWa.DOCUMENTO;
            case "audio"    -> TipoMensajeWa.AUDIO;
            default         -> TipoMensajeWa.DESCONOCIDO;
        };
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) digits = digits.substring(2);
        if (digits.length() == 9) return "+51" + digits;
        return digits;
    }
}
