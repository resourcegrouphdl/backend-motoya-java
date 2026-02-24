package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.CategoriaPlantilla;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlantillaWhatsapp {

    private String plantillaId;
    private String nombre;
    private CategoriaPlantilla categoria;
    private String cuerpo;
    private List<VariablePlantilla> variables;

    private Boolean activa;
    private Boolean aprobadaPorMeta;
    /** ID de la plantilla registrada en Meta Business */
    private String metaTemplateId;

    private String createdAt;
}
