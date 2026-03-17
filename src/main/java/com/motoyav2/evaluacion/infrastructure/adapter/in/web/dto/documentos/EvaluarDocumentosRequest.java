package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request para evaluar documentos de un cliente (titular o fiador).
 *
 * evaluaciones: mapa de tipoDocumento → { estado, observaciones }
 *   estado: 'aprobado' | 'observado' | 'rechazado'
 *
 * Ejemplo:
 * {
 *   "clienteId": "abc123",
 *   "evaluadorId": "user456",
 *   "evaluadorNombre": "Juan Pérez",
 *   "evaluaciones": {
 *     "dniFrente": { "estado": "aprobado", "observaciones": "" },
 *     "dniReverso": { "estado": "observado", "observaciones": "Imagen borrosa" }
 *   }
 * }
 */
@Getter
@NoArgsConstructor
public class EvaluarDocumentosRequest {

    private String clienteId;          // ID en clientes_v1
    private String evaluadorId;
    private String evaluadorNombre;

    /** tipoDocumento → { estado: string, observaciones: string } */
    private Map<String, EvaluacionDocumentoItem> evaluaciones;

    @Getter
    @NoArgsConstructor
    public static class EvaluacionDocumentoItem {
        private String estado;          // aprobado | observado | rechazado
        private String observaciones;
    }
}
