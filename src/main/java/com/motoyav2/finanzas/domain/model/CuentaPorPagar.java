package com.motoyav2.finanzas.domain.model;

import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class CuentaPorPagar {
    String id;
    TipoCuenta tipo;
    String proveedor;
    String descripcion;
    String numeroDocumento;
    BigDecimal montoTotal;
    int numeroCuotas;
    EstadoCuenta estado;
    LocalDate fechaVencimiento;
    LocalDateTime creadoEn;
    List<CuotaCuenta> cuotas;

    /** Estado derivado de las cuotas. */
    public static EstadoCuenta calcularEstado(List<CuotaCuenta> cuotas) {
        boolean todoPagado = cuotas.stream().allMatch(c -> c.getEstado() == EstadoCuenta.PAGADO);
        if (todoPagado) return EstadoCuenta.PAGADO;
        boolean tieneVencida = cuotas.stream().anyMatch(c -> c.getEstado() == EstadoCuenta.VENCIDO);
        if (tieneVencida) return EstadoCuenta.VENCIDO;
        return EstadoCuenta.PENDIENTE;
    }

    /** Fecha de vencimiento = próxima cuota pendiente o vencida. */
    public static LocalDate calcularFechaVencimiento(List<CuotaCuenta> cuotas) {
        return cuotas.stream()
                .filter(c -> c.getEstado() != EstadoCuenta.PAGADO)
                .map(CuotaCuenta::getFechaVencimiento)
                .min(LocalDate::compareTo)
                .orElse(null);
    }
}
