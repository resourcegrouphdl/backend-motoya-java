package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.EstadoComprobante;
import com.motoyav2.cobranza.domain.enums.TipoComprobante;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComprobantePago {

    private String comprobanteId;
    private String contratoId;
    private String voucherId;

    private String serie;
    private String correlativo;
    private TipoComprobante tipo;
    private EstadoComprobante estado;

    private Emisor emisor;
    private ReceptorComprobante receptor;
    private List<ItemComprobante> items;

    private Double subtotal;
    private Double igv;
    private Double total;

    private String sunatCdr;
    private Integer intentosSunat;
    private String gcsUrl;

    private String createdAt;
    private String updatedAt;
}
