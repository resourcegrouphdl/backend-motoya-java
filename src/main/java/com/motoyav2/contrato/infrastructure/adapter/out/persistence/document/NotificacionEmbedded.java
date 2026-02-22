package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class NotificacionEmbedded {
    private String tipo;
    private String mensaje;
    private String destinatario;
    private Timestamp fecha;
    private Boolean exitoso;
}
