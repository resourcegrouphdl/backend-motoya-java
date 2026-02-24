package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.EstadoMensajeWa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MensajeWhatsapp {

    private String mensajeId;
    private String contratoId;
    private String clienteId;
    private String agenteId;

    /** WhatsApp Message ID retornado por Twilio */
    private String wamid;
    private String telefono;
    private String plantillaId;
    private String cuerpoMensaje;

    private EstadoMensajeWa estado;
    private String errorMensaje;

    private String createdAt;
    private String updatedAt;
}
