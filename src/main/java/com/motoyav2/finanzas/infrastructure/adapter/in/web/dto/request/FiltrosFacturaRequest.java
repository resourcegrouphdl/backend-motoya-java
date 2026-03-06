package com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request;

import com.motoyav2.finanzas.domain.enums.EstadoPago;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FiltrosFacturaRequest {
    private String tiendaId;
    private EstadoPago estado;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
}
