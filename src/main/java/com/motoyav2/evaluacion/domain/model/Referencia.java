package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Referencia {
    String id;
    Integer numero;         // 1 | 2 | 3
    String nombre;
    String apellidos;
    String telefono;
    String parentesco;
    String titularId;
    String estadoVerificacion;  // pendiente | contactado | verificado | no_contactado | rechazado
    String resultadoContacto;   // positivo - ok | no contesta | negativo
    Integer scoreVerificacion;
    String actitudDuranteContacto;
    String observaciones;
    Timestamp fechaContacto;
    Boolean rechazada;
    Timestamp fechaRechazo;

    // ── Verificación automática vía WhatsApp ──────────────────────────────────
    /** ID de la solicitud a la que pertenece — necesario para correlación webhook y umbral. */
    String solicitudId;
    /** wamid retornado por Meta al enviar el mensaje. */
    String wamid;
    /** Texto raw de la respuesta recibida por WhatsApp. */
    String respuestaWhatsapp;
    /** Clasificación de Claude: POSITIVA | NEGATIVA | DUDOSA. */
    String clasificacionClaude;
    /** Confianza de Claude: 0.0–1.0. */
    Double confianzaClaude;
    /** Momento en que se envió el mensaje WhatsApp de verificación. */
    Timestamp fechaEnvioWhatsapp;
    /** "automatico" si fue por WhatsApp/Claude, "manual" si el evaluador lo hizo directamente. */
    String metodoVerificacion;

    Timestamp createdAt;
    Timestamp updatedAt;

    public boolean estaVerificada() {
        return "verificado".equals(estadoVerificacion);
    }
}
