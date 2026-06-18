package com.motoyav2.notifications.application.usecase;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.CasoCobranzaRepository;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import com.motoyav2.whatsapp.domain.event.CobranzaWaRecibidoEvent;
import com.motoyav2.whatsapp.domain.event.EvaluacionParticipanteWaRecibidoEvent;
import com.motoyav2.whatsapp.domain.event.NumeroDesconocidoWaEvent;
import com.motoyav2.whatsapp.domain.event.ReferenciaWaRecibidoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;

/**
 * Enrutador central de mensajes entrantes de WhatsApp.
 *
 * Responsabilidad: resolver el contexto del mensaje (quién envió → a qué dominio pertenece)
 * y publicar el evento de dominio correspondiente. El procesamiento lo realizan los handlers
 * en com.motoyav2.whatsapp.application.service.
 *
 * Orden de resolución:
 *   1. ¿Referencia activa con wa_enviado?       → ReferenciaWaRecibidoEvent
 *   2. ¿Titular de solicitud activa?             → EvaluacionParticipanteWaRecibidoEvent(esFiador=false)
 *   3. ¿Fiador de solicitud activa?              → EvaluacionParticipanteWaRecibidoEvent(esFiador=true)
 *   4. ¿Cliente con caso cobranza activo?        → CobranzaWaRecibidoEvent
 *   5. Número desconocido                        → NumeroDesconocidoWaEvent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMessageDispatcherImpl implements WhatsAppMessageDispatcher {

    private static final List<String> CICLOS_INACTIVOS =
            List.of("PAGADO_TOTAL", "JUDICIAL", "CASTIGADO", "CERRADO");

    private final ReferenciaRepository    referenciaRepository;
    private final SolicitudRepository     solicitudRepository;
    private final CasoCobranzaRepository  casoCobranzaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Firestore               firestore;

    @Override
    public Mono<Void> dispatch(String fromPhone, String text, String mediaType, String mediaUrl) {
        String phone = normalizePhone(fromPhone);
        log.info("[DISPATCHER] fromRaw={} phoneNorm={} text={}", fromPhone, phone, text);
        if (phone.isBlank()) return Mono.empty();

        // ── 1. ¿Es referencia? ────────────────────────────────────────────────
        return referenciaRepository.findByTelefonoAndEstadoWaEnviado(phone)
                .flatMap(ref -> {
                    log.info("[DISPATCHER] Contexto=REFERENCIA | refId={} phone={}", ref.getId(), phone);
                    String nombre = buildNombre(ref.getNombre(), ref.getApellidos());
                    eventPublisher.publishEvent(new ReferenciaWaRecibidoEvent(
                            ref.getSolicitudId(), ref.getId(), phone, nombre, text, mediaType, mediaUrl));
                    return Mono.<Void>empty();
                })
                // ── 2. ¿Es titular? ───────────────────────────────────────────
                .switchIfEmpty(solicitudRepository.findActivaByTitularTelefono(phone)
                        .flatMap(sol -> {
                            log.info("[DISPATCHER] Contexto=TITULAR | solicitudId={} phone={}", sol.getId(), phone);
                            eventPublisher.publishEvent(new EvaluacionParticipanteWaRecibidoEvent(
                                    sol.getId(), phone, sol.getTitularNombreCompleto(),
                                    text, mediaType, mediaUrl, false));
                            return Mono.<Void>empty();
                        }))
                // ── 3. ¿Es fiador? ────────────────────────────────────────────
                .switchIfEmpty(solicitudRepository.findActivaByFiadorTelefono(phone)
                        .flatMap(sol -> {
                            log.info("[DISPATCHER] Contexto=FIADOR | solicitudId={} phone={}", sol.getId(), phone);
                            eventPublisher.publishEvent(new EvaluacionParticipanteWaRecibidoEvent(
                                    sol.getId(), phone, "Fiador",
                                    text, mediaType, mediaUrl, true));
                            return Mono.<Void>empty();
                        }))
                // ── 4. ¿Es cliente de cobranza? ───────────────────────────────
                .switchIfEmpty(buscarCasoActivoPorTelefono(phone)
                        .flatMap(caso -> {
                            log.info("[DISPATCHER] Contexto=COBRANZA | contratoId={} phone={}", caso.getContratoId(), phone);
                            eventPublisher.publishEvent(new CobranzaWaRecibidoEvent(
                                    caso.getContratoId(), caso.getStoreId(),
                                    caso.getClienteNombre(), caso.getAgenteAsignadoId(),
                                    phone, text, mediaType, mediaUrl));
                            return Mono.<Void>empty();
                        }))
                // ── 5. Número desconocido ─────────────────────────────────────
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[DISPATCHER] Contexto=DESCONOCIDO phone={}", phone);
                    eventPublisher.publishEvent(new NumeroDesconocidoWaEvent(phone, text, mediaType, mediaUrl));
                    return Mono.<Void>empty();
                }))
                .onErrorResume(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("index")) {
                        log.error("[DISPATCHER] *** ÍNDICE FIRESTORE FALTANTE *** phone={}: {}", phone, e.getMessage());
                    } else {
                        log.error("[DISPATCHER] Error resolviendo contexto phone={}: {}", phone, e.getMessage());
                    }
                    return Mono.empty();
                });
    }

    private Mono<CasoCobranzaDocument> buscarCasoActivoPorTelefono(String normalizedPhone) {
        String tel9 = toCasoTelefono(normalizedPhone);
        if (tel9.isBlank()) return Mono.empty();
        return casoCobranzaRepository.findByClienteTelefono(tel9)
                .filter(c -> c.getCicloVida() == null || !CICLOS_INACTIVOS.contains(c.getCicloVida()))
                .next()
                .switchIfEmpty(buscarPorFiadorTelefono(tel9))
                .switchIfEmpty(buscarPorTelefonoAdicional(tel9));
    }

    private Mono<CasoCobranzaDocument> buscarPorFiadorTelefono(String tel9) {
        return Mono.fromCallable(() -> {
            var snap = firestore.collection("cobranzas-casos")
                    .whereEqualTo("fiador.telefono", tel9)
                    .limit(5).get().get();
            return snap.getDocuments().stream()
                    .map(doc -> doc.toObject(CasoCobranzaDocument.class))
                    .filter(Objects::nonNull)
                    .filter(c -> c.getCicloVida() == null || !CICLOS_INACTIVOS.contains(c.getCicloVida()))
                    .findFirst().orElse(null);
        }).subscribeOn(Schedulers.boundedElastic())
        .filter(Objects::nonNull);
    }

    private Mono<CasoCobranzaDocument> buscarPorTelefonoAdicional(String tel9) {
        return Mono.fromCallable(() -> {
            var snap = firestore.collection("cobranzas-casos")
                    .whereArrayContains("telefonosAdicionales", tel9)
                    .limit(5).get().get();
            return snap.getDocuments().stream()
                    .map(doc -> doc.toObject(CasoCobranzaDocument.class))
                    .filter(Objects::nonNull)
                    .filter(c -> c.getCicloVida() == null || !CICLOS_INACTIVOS.contains(c.getCicloVida()))
                    .findFirst().orElse(null);
        }).subscribeOn(Schedulers.boundedElastic())
        .filter(Objects::nonNull);
    }

    private String toCasoTelefono(String normalizedPhone) {
        if (normalizedPhone == null) return "";
        String digits = normalizedPhone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        if (digits.length() == 9) return digits;
        return "";
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) digits = digits.substring(2);
        if (digits.length() == 9) return "+51" + digits;
        return digits;
    }

    private String buildNombre(String nombre, String apellidos) {
        String n = nombre   != null ? nombre.trim()   : "";
        String a = apellidos != null ? apellidos.trim() : "";
        return (n + " " + a).trim();
    }
}
