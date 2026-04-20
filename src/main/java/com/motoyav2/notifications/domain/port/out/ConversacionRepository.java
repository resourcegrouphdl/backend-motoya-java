package com.motoyav2.notifications.domain.port.out;

import com.motoyav2.notifications.domain.model.conversacion.ConversacionWa;
import com.motoyav2.notifications.domain.model.conversacion.MensajeWa;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversacionRepository {

    /** Crea o actualiza la cabecera de la conversación (upsert por id). */
    Mono<Void> upsertConversacion(ConversacionWa conversacion);

    /** Agrega un mensaje al array de mensajes de forma atómica (arrayUnion). */
    Mono<Void> agregarMensaje(String conversacionId, MensajeWa mensaje);

    /** Devuelve la conversación por id determinístico {solicitudId}_{ROL}_{telefono}. */
    Mono<ConversacionWa> findById(String id);

    /** Devuelve todas las conversaciones de una solicitud, ordenadas por ultimaActividad. */
    Flux<ConversacionWa> findBySolicitudId(String solicitudId);

    /** Genera el id determinístico para una conversación. */
    static String buildId(String solicitudId, RolParticipante rol, String telefono) {
        String normalized = telefono != null ? telefono.replaceAll("[^0-9]", "") : "0";
        return solicitudId + "_" + rol.name() + "_" + normalized;
    }
}
