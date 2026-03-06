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
    String vendedorId;
    String vendedorNombre;
    String tiendaId;
    String tiendaNombre;
    LocalDate periodoInicio;
    LocalDate periodoFin;
    int totalVentas;
    BigDecimal montoComision;
    EstadoPago estado;
    LocalDateTime pagadoEn;
}
