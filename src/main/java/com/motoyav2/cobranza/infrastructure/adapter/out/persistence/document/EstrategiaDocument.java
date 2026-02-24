package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Reglas de cobranza automática evaluadas por el job cada 15 minutos.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "estrategias")
public class EstrategiaDocument {

    @DocumentId
    private String id;

    /** Ej: "WhatsApp mora temprana D+3" */
    private String nombre;
    /** NivelEstrategia */
    private String nivel;
    /** CanalContacto: WHATSAPP | SMS | LLAMADA | EMAIL | VISITA */
    private String canal;

    /** Solo si canal == WHATSAPP */
    private String plantillaId;
    /** Template del mensaje o guión de llamada */
    private String mensaje;

    /** Si el job debe disparar esta estrategia */
    private Boolean activo;

    private Integer diasMoraDesde;
    /** null = sin límite */
    private Integer diasMoraHasta;
    /** Cada cuántos días se repite si el caso sigue en mora */
    private Integer frecuenciaDias;

    /** Hora mínima de disparo "HH:MM". Ej: "09:00" */
    private String horarioDesde;
    /** Hora máxima "HH:MM". Ej: "18:00" */
    private String horarioHasta;

    /** null = global a todas las tiendas */
    private String storeId;
    /** Menor número = mayor prioridad */
    private Integer prioridad;

    private Date creadoEn;
    private Date actualizadoEn;
}
