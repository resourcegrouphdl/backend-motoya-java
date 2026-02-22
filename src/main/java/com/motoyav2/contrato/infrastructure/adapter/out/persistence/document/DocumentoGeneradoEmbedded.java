package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class DocumentoGeneradoEmbedded {
    private String id;
    private String tipo;
    private String urlDocumento;
    private String nombreArchivo;
    private Timestamp fechaGeneracion;
    private String generadoPor;
    private Integer versionDocumento;
    private String descargadoPor;
    private Timestamp fechaDescarga;
}
