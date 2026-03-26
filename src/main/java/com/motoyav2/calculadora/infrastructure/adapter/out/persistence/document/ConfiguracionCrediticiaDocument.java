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
 *
 * Estructura de plazos (cada elemento):
 *   periodos  (Long)   — número de cuotas
 *   frecuencia (String) — "WEEKLY" | "MONTHLY"
 *   tea       (Double)
 *   etiqueta  (String)
 *
 * Estructura de comisionDefault:
 *   tipo      (String)  — "FIXED" | "PERCENTAGE"
 *   valor     (Double)
 *   financiada (Boolean)
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
    private Map<String, Object> comisionDefault;
    private Double teaDefault;
    private List<Map<String, Object>> plazos;
    private Timestamp actualizadoEn;
    private String actualizadoPor;
}