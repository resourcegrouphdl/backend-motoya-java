package com.motoyav2.finanzas.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class DashboardFinanzas {
    long totalFacturasPendientes;
    BigDecimal montoFacturasPendientes;
    long pagosTiendaHoy;
    long pagosVencidos;
    BigDecimal egresosDelMes;
    long comisionesPendientes;
    List<ProximoPago> proximosPagos;
    List<AlertaFinanciera> alertas;

    @Value
    @Builder
    public static class ProximoPago {
        String id;
        String descripcion;
        BigDecimal monto;
        String fechaVencimiento;
        String estado;
        String modulo;
        String ruta;
    }
}
