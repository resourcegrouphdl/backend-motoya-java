package com.motoyav2.notifications.domain.port.in;

import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import reactor.core.publisher.Mono;

public interface RegistrarMensajeConversacionUseCase {

    /**
     * Persiste un mensaje en la conversación correspondiente.
     * Crea la conversación si no existe.
     *
     * @param solicitudId        ID de la solicitud
     * @param telefono           Teléfono normalizado del participante
     * @param nombreParticipante Nombre del participante
     * @param rol                TITULAR | FIADOR | REFERENCIA
     * @param direccion          INBOUND | OUTBOUND
     * @param tipo               TEXTO | IMAGEN | PDF | AUDIO | DOCUMENTO
     * @param contenido          Texto del mensaje o descripción del archivo
     * @param mediaUrl           URL del archivo (null si es texto)
     * @param enviadorNombre     Nombre del evaluador (null = sistema automático)
     * @param claudeClasificacion Clasificación Claude si aplica (null en otro caso)
     * @param claudeConfianza    Confianza Claude (null en otro caso)
     */
    Mono<Void> registrar(
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
            Double claudeConfianza
    );
}
