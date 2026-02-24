package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class EvidenciaDocumentoEmbedded {
    private String id;
    private String tipoEvidencia;
    private String urlEvidencia;
    private String nombreArchivo;
    private String tipoArchivo;
    private Long tamanioBytes;
    private Timestamp fechaSubida;
    private String descripcion;
    private String estadoValidacion;
    private String validadoPor;
    private Timestamp fechaValidacion;
    private String observacionesValidacion;
}