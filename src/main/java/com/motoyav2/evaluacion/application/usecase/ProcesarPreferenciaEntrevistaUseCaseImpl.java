package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.service.PreferenciaEntrevistaClasificadorService;
import com.motoyav2.evaluacion.domain.port.in.ProcesarPreferenciaEntrevistaUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesarPreferenciaEntrevistaUseCaseImpl implements ProcesarPreferenciaEntrevistaUseCase {

    private final SolicitudRepository                          solicitudRepository;
    private final PreferenciaEntrevistaClasificadorService     clasificador;
    private final MetaWhatsAppNotificationAdapter              whatsApp;
    private final RegistrarMensajeConversacionUseCase          registrarMensaje;

    @Override
    public Mono<Void> procesar(String solicitudId, String fromPhone, String textoRespuesta, boolean esFiador) {
        RolParticipante rol = esFiador ? RolParticipante.FIADOR : RolParticipante.TITULAR;

        return solicitudRepository.findById(solicitudId)
                .flatMap(solicitud -> clasificador.extraer(textoRespuesta)
                        .flatMap(resultado -> {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("updatedAt", Timestamp.now());
                            String respuestaWa;

                            String nombre = esFiador
                                    ? null  // nombre lo tomamos del solicitudId si lo necesitamos
                                    : solicitud.getTitularNombreCompleto();
                            String primerNombre = nombre != null && nombre.contains(" ")
                                    ? nombre.split(" ")[0] : (nombre != null ? nombre : "");

                            if (resultado.tienePreferencia()
                                    && resultado.fechaIso() != null
                                    && !"BAJA".equals(resultado.confianza())) {

                                String fechaHora = resultado.hora() != null
                                        ? resultado.fechaIso() + " a las " + resultado.hora()
                                        : resultado.fechaIso();
                                updates.put("entrevista.fechaPreferida", resultado.fechaIso());
                                updates.put("entrevista.horaPreferida",  resultado.hora());
                                updates.put("entrevista.estadoConversacion", "PREFERENCIA_CAPTURADA");
                                updates.put("entrevista.textoPreferenciaOriginal", textoRespuesta);

                                respuestaWa = "Perfecto" + (primerNombre.isBlank() ? "" : " " + primerNombre) +
                                        " ✅ Hemos tomado nota de tu preferencia: *" + fechaHora + "*.\n" +
                                        "Un asesor confirmará contigo el horario.\n_Motoya Digital_";
                                log.info("[PREF-ENTREVISTA] Preferencia capturada para solicitud={} fecha={} hora={}",
                                        solicitudId, resultado.fechaIso(), resultado.hora());
                            } else {
                                updates.put("entrevista.estadoConversacion", "PREFERENCIA_LIBRE");
                                respuestaWa = "Gracias" + (primerNombre.isBlank() ? "" : " " + primerNombre) +
                                        " 🙌 Un asesor de *Motoya Digital* se contactará contigo a la brevedad.\n_Motoya Digital_";
                                log.info("[PREF-ENTREVISTA] Sin preferencia clara para solicitud={}", solicitudId);
                            }

                            String finalNombre   = nombre != null ? nombre : "Participante";
                            String finalRespuesta = respuestaWa;

                            return solicitudRepository.updateFields(solicitudId, updates)
                                    // Registrar mensaje entrante del cliente
                                    .then(registrarMensaje.registrar(
                                            solicitudId, fromPhone, finalNombre, rol,
                                            DireccionMensaje.INBOUND, TipoMensajeWa.TEXTO,
                                            textoRespuesta, null, null,
                                            resultado.tienePreferencia() ? "PREFERENCIA" : "SIN_PREFERENCIA",
                                            null))
                                    // Enviar respuesta automática
                                    .then(whatsApp.sendText(fromPhone, finalRespuesta))
                                    // Registrar respuesta automática saliente
                                    .flatMap(wamid -> registrarMensaje.registrar(
                                            solicitudId, fromPhone, finalNombre, rol,
                                            DireccionMensaje.OUTBOUND, TipoMensajeWa.TEXTO,
                                            finalRespuesta, null, "Sistema Automático",
                                            null, null))
                                    .then();
                        })
                )
                .onErrorResume(e -> {
                    log.error("[PREF-ENTREVISTA] Error procesando preferencia solicitud={}: {}", solicitudId, e.getMessage());
                    return Mono.empty();
                });
    }
}
