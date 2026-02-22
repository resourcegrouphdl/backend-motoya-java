package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class DocumentoAdjuntoEmbedded {
    private String url;
    private String nombreArchivo;
    private String estadoValidacion;
    private String observacion;
    private String validadoPor;
    private Timestamp fechaValidacion;
}
