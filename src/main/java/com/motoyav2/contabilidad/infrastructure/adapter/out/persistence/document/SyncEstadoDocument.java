package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/** Colección: contabilidad_sync_estado / doc-id = "vouchers" | "facturas" | "comisiones" */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncEstadoDocument {
    private String id;
    private Date ultimaSincronizacion;
    private long totalProcesados;
    private Date actualizadoEn;
}
