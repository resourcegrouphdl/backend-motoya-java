package com.motoyav2.finanzas.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class PagoComisionVendedor {
    String id;
    // Datos del vendedor
    String vendedorId;
    String vendedorNombre;
    String vendedorDocumento;
    String vendedorTipoDocumento;
    String vendedorEmail;
    String vendedorPhone;
    // Datos de tienda
    String tiendaId;
    String tiendaNombre;
    // Período
    String periodoCorte;      // "2026-03-15" | "2026-04-01"
    String tipoPeriodo;       // "PRIMERA_QUINCENA" | "SEGUNDA_QUINCENA"
    String periodoDesde;      // ISO date
    String periodoHasta;      // ISO date
    // Comisiones agrupadas
    List<String> comisionIds;
    int totalVentas;
    BigDecimal montoTotal;
    // Datos de pago (null hasta confirmar)
    String metodoPago;
    String entidadBancaria;
    String cuentaDestino;
    String numeroOperacion;
    String voucherUrl;
    String voucherGcsPath;
    String comprobanteUrl;    // URL del PDF comprobante generado tras el pago
    // Estado
    String estado;            // "PENDIENTE" | "PAGADO"
    String registradoPor;
    String creadoEn;
    String pagadoEn;
    String actualizadoEn;
}
