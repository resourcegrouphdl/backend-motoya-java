package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.VariablePlantillaDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Plantillas aprobadas por Meta para WhatsApp Business.
 * ID del documento: slug legible. Ej: recordatorio_pago_mora_temprana
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "plantillas_whatsapp")
public class PlantillaWhatsappDocument {

    @DocumentId
    private String id;

    private String nombre;
    /** CategoriaPlantilla */
    private String categoria;
    /** NivelEstrategia — puede ser null */
    private String nivelMora;

    /** Template con {{variable}} placeholders */
    private String cuerpo;
    private List<VariablePlantillaDocument> variables;

    /** Si puede usarse — desactivar sin eliminar */
    private Boolean activa;
    /** Meta debe aprobar plantillas de negocio antes de producción */
    private Boolean aprobadaPorMeta;
    private String metaTemplateName;

    private Date creadoEn;
    private Date actualizadoEn;
}
