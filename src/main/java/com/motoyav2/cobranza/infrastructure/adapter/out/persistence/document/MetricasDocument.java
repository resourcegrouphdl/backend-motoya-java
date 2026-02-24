package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.MetricasAgenteDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

/**
 * Singleton de KPIs pre-calculados.
 * ID fijo del documento: "resumen_actual"
 * Sobreescrito por job nocturno y cada 15 min.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "metricas")
public class MetricasDocument {

    @DocumentId
    private String id;

    private Integer promesasVencenHoy;
    private Integer promesasIncumplidas;
    private Integer vouchersPendientes;
    private Integer casosCriticos;
    private Integer casosActivos;

    private Double moraTotal;
    private Double recuperacionMes;
    /** (recuperacionMes / moraTotal) * 100 */
    private Double tasaRecuperacion;
    /** % de gestiones del mes generadas por estrategias automáticas */
    private Double porcentajeAutomatizado;

    private Integer totalComprobantesEmitidos;
    private Double montoComprobantesEmitidos;

    /** Mapa uid → MetricasAgenteDocument */
    private Map<String, MetricasAgenteDocument> agentes;

    private Date ultimaActualizacion;
}
