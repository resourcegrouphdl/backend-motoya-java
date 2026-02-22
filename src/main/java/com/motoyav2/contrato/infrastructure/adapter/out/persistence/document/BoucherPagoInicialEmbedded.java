package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class BoucherPagoInicialEmbedded {
    private String id;
    private String urlDocumento;
    private String nombreArchivo;
    private String tipoArchivo;
    private Integer tamanioBytes;
    private Timestamp fechaSubida;
    private String estadoValidacion;
    private String observacionesValidacion;
    private String validadoPor;
    private Timestamp fechaValidacion;
}
