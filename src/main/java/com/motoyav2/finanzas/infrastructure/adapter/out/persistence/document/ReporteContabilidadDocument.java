package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Documento Firestore: reportes_contabilidad
 * Almacena metadata de cada reporte mensual generado.
 */
@Data
@NoArgsConstructor
public class ReporteContabilidadDocument {

    @DocumentId
    private String id;                  // {anio}-{mes}, ej: 2026-03

    private String mes;                 // "2026-03"
    private String anio;                // "2026"
    private String mesNombre;           // "Marzo 2026"

    // Totales agregados
    private Double totalPagosComisiones;   // suma de pagos confirmados a vendedores
    private Integer cantidadPagosComisiones;

    private Double totalPagosTiendas;      // suma de pagos confirmados a tiendas (facturas)
    private Integer cantidadPagosTiendas;

    private Double totalCuotasCxP;         // cuotas CxP pagadas en el mes
    private Integer cantidadCuotasCxP;

    private Double totalEgresos;           // suma de los 3 anteriores

    // URL del PDF generado
    private String reporteUrl;             // Firebase Storage download URL

    private String estado;                 // GENERADO | ERROR
    private String generadoEn;
    private String actualizadoEn;
}
