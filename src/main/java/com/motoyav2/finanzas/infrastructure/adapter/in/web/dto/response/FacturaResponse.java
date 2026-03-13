package com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de respuesta que coincide exactamente con el modelo FacturaTienda del frontend.
 * Solo expone los campos que el frontend consume. Los campos del vehículo y documentos
 * se omiten del listado; si el detalle los necesita, usar un endpoint separado.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FacturaResponse {

    String id;
    String numero;
    String tiendaId;
    String tiendaNombre;
    String ventaId;
    String clienteNombre;
    String motoModelo;
    BigDecimal montoTotal;
    LocalDate fechaFactura;
    int condicionPago;
    EstadoPago estado;
    List<PagoFactura> pagos;

    public static FacturaResponse from(Factura factura) {
        return FacturaResponse.builder()
                .id(factura.getId())
                .numero(factura.getNumero())
                .tiendaId(factura.getTiendaId())
                .tiendaNombre(factura.getTiendaNombre())
                .ventaId(factura.getVentaId())
                .clienteNombre(factura.getClienteNombre())
                .motoModelo(factura.getMotoModelo())
                .montoTotal(factura.getMontoTotal())
                .fechaFactura(factura.getFechaFactura())
                .condicionPago(factura.getCondicionPago())
                .estado(factura.getEstado())
                .pagos(factura.getPagos())
                .build();
    }
}
