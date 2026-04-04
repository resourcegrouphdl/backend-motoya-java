package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoComisionDocument {
    private String id;
    private String vendedorId;
    private String vendedorNombre;
    private String vendedorDocumento;
    private String vendedorTipoDocumento;
    private String vendedorEmail;
    private String vendedorPhone;
    private String tiendaId;
    private String tiendaNombre;
    private String periodoCorte;
    private String tipoPeriodo;
    private String periodoDesde;
    private String periodoHasta;
    private List<String> comisionIds;
    private Integer totalVentas;
    private Double montoTotal;
    private String metodoPago;
    private String entidadBancaria;
    private String cuentaDestino;
    private String numeroOperacion;
    private String voucherUrl;
    private String voucherGcsPath;
    private String comprobanteUrl;
    private String estado;
    private String registradoPor;
    private String creadoEn;
    private String pagadoEn;
    private String actualizadoEn;
}
