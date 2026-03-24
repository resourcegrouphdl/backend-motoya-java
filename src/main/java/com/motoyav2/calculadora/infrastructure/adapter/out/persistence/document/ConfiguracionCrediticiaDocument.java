package com.motoyav2.calculadora.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Documento Firestore para la configuración crediticia.
 * Colección: configuracion_crediticia / Document ID: default
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionCrediticiaDocument {

    private String id;
    private Double gastosAdministrativos;
    private Double porcentajeInicialMinima;
    private Double montoMaximoFinanciar;
    private Double montoMinimoFinanciar;
    private Double tasaSeguroDesgravamenMensual;
    private Double comisionDesembolso;
    private Double teaDefault;

    /**
     * Lista de plazos. Cada elemento es un Map con campos:
     *   meses    (Long)
     *   tea      (Double)
     *   etiqueta (String)
     */
    private List<Map<String, Object>> plazos;

    private Timestamp actualizadoEn;
    private String actualizadoPor;
}
