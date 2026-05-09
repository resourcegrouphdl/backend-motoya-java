package com.motoyav2.finanzas.domain.model;

import com.motoyav2.finanzas.domain.enums.EstadoPago;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class ComisionVendedor {
    String id;
    String contratoId;
    String solicitudId;
    // Datos del cliente (snapshot al momento de creación)
    String clienteNombre;
    String clienteDocumento;
    // Datos del vendedor (de users)
    String vendedorId;
    String vendedorNombre;
    String vendedorEmail;
    String vendedorPhone;
    String vendedorDocumento;
    String vendedorTipoDocumento;
    String vendedorUserType;
    // Datos de tienda y período
    String tiendaId;
    String tiendaNombre;
    LocalDate periodoInicio;
    LocalDate periodoFin;
    int totalVentas;
    BigDecimal montoComision;
    EstadoPago estado;
    String pagoId;        // referencia al PagoComisionVendedor cuando está EN_PROCESO o PAGADO
    LocalDateTime pagadoEn;
}
