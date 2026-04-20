package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.application.service.ReferenciaClasificadorService;
import com.motoyav2.evaluacion.application.service.ReferenciaClasificadorService.Clasificacion;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.Referencia;
import com.motoyav2.evaluacion.domain.port.in.ActualizarSemaforoReferenciasUseCase;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.ProcesarRespuestaReferenciaUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Procesa la respuesta de WhatsApp de una referencia personal:
 * 1. Correlaciona el teléfono con la referencia activa (estado wa_enviado)
 * 2. Clasifica la respuesta con Claude
 * 3. Actualiza el estado de la referencia
 * 4. Si >= 2 referencias verificadas → auto-transición a referencias_aprobadas
 *
 * Mínimo de referencias positivas requeridas: 2 (configurable en el futuro vía Firestore).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesarRespuestaReferenciaUseCaseImpl implements ProcesarRespuestaReferenciaUseCase {

    private static final int    MINIMO_VERIFICADAS = 2;
    private static final String USUARIO_SISTEMA    = "sistema-automatico";
    private static final String NOMBRE_SISTEMA     = "Verificación Automática WhatsApp";

    private final ReferenciaRepository               referenciaRepository;
    private final SolicitudRepository                solicitudRepository;
    private final ReferenciaClasificadorService      clasificador;
    private final CambiarEstadoUseCase               cambiarEstadoUseCase;
    private final ActualizarSemaforoReferenciasUseCase actualizarSemaforo;
    private final RegistrarMensajeConversacionUseCase  registrarMensaje;

    @Override
    public Mono<Void> ejecutar(String fromPhone, String messageText) {
        return referenciaRepository.findByTelefonoAndEstadoWaEnviado(normalizePhone(fromPhone))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[PROC-REF] No hay referencia activa para phone={}", fromPhone);
                    return Mono.empty();
                }))
                .flatMap(ref -> clasificador.clasificar(messageText)
                        .flatMap(resultado -> {
                            // Registrar en historial de conversación
                            String nombreRef = (ref.getNombre() != null ? ref.getNombre() : "") + " " +
                                    (ref.getApellidos() != null ? ref.getApellidos() : "");
                            registrarMensaje.registrar(
                                    ref.getSolicitudId(), normalizePhone(fromPhone), nombreRef.trim(),
                                    RolParticipante.REFERENCIA, DireccionMensaje.INBOUND, TipoMensajeWa.TEXTO,
                                    messageText, null, null,
                                    resultado.clasificacion().name(), resultado.confianza()
                            ).subscribe(null, e -> log.warn("[PROC-REF] Error registrando msg: {}", e.getMessage()));

                            return actualizarReferencia(ref, messageText, resultado);
                        })
                        .flatMap(refActualizada -> actualizarSemaforo
                                .actualizar(refActualizada.getSolicitudId())
                                .then(verificarUmbral(refActualizada)))
                );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Referencia> actualizarReferencia(
            Referencia ref, String respuestaRaw, ReferenciaClasificadorService.ResultadoClasificacion res) {

        Map<String, Object> updates = new HashMap<>();
        updates.put("respuestaWhatsapp",  respuestaRaw);
        updates.put("clasificacionClaude", res.clasificacion().name());
        updates.put("confianzaClaude",     res.confianza());
        updates.put("fechaContacto",       Timestamp.now());
        updates.put("updatedAt",           Timestamp.now());
        updates.put("metodoVerificacion",  "automatico");

        switch (res.clasificacion()) {
            case POSITIVA -> {
                updates.put("estadoVerificacion", "verificado");
                updates.put("resultadoContacto",  "positivo - ok");
            }
            case NEGATIVA -> {
                updates.put("estadoVerificacion", "rechazado");
                updates.put("resultadoContacto",  "negativo");
            }
            case DUDOSA -> {
                updates.put("estadoVerificacion", "no_contactado");
                updates.put("resultadoContacto",  "respuesta ambigua");
                log.info("[PROC-REF] DUDOSA para refId={} — requiere revisión manual", ref.getId());
            }
        }

        return referenciaRepository.updateFields(ref.getId(), updates)
                .then(referenciaRepository.findById(ref.getId()))
                .doOnSuccess(r -> log.info("[PROC-REF] refId={} → {} ({})", ref.getId(),
                        updates.get("estadoVerificacion"), res.clasificacion()));
    }

    private Mono<Void> verificarUmbral(Referencia refActualizada) {
        String solicitudId = refActualizada.getSolicitudId();
        if (solicitudId == null || solicitudId.isBlank()) return Mono.empty();

        return solicitudRepository.findById(solicitudId)
                .flatMap(solicitud -> {
                    List<String> refsIds = solicitud.getReferenciasIds();
                    if (refsIds == null || refsIds.isEmpty()) return Mono.empty();

                    return referenciaRepository.findByIds(refsIds)
                            .filter(r -> "verificado".equals(r.getEstadoVerificacion()))
                            .count()
                            .flatMap(verificadas -> {
                                log.info("[PROC-REF] solicitud={} verificadas={}/{}", solicitudId, verificadas, refsIds.size());

                                if (verificadas < MINIMO_VERIFICADAS) return Mono.empty();

                                // Solo auto-transicionar si el estado actual lo permite
                                String estadoActual = solicitud.getEstado() != null
                                        ? solicitud.getEstado().name() : "";
                                if (!EstadoSolicitud.FIADOR_APROBADO.name().equals(estadoActual)) {
                                    log.info("[PROC-REF] Umbral alcanzado pero estado={} no permite auto-transición", estadoActual);
                                    return Mono.empty();
                                }

                                log.info("[PROC-REF] Auto-transición a REFERENCIAS_APROBADAS para solicitud={}", solicitudId);
                                return cambiarEstadoUseCase.ejecutar(new CambiarEstadoCommand(
                                        solicitudId,
                                        EstadoSolicitud.REFERENCIAS_APROBADAS,
                                        USUARIO_SISTEMA,
                                        NOMBRE_SISTEMA,
                                        verificadas + " referencias verificadas automáticamente vía WhatsApp"
                                )).then();
                            });
                })
                .onErrorResume(ex -> {
                    log.error("[PROC-REF] Error en verificarUmbral: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) digits = digits.substring(2);
        if (digits.length() == 9) return "+51" + digits;
        return digits;
    }
}
