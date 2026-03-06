package com.motoyav2.finanzas.domain.model;

import com.motoyav2.finanzas.domain.enums.EstadoPago;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class Factura {
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

    /** Estado derivado del peor estado de los pagos. */
    public static EstadoPago calcularEstado(List<PagoFactura> pagos) {
        boolean todoPagado = pagos.stream().allMatch(p -> p.getEstado() == EstadoPago.PAGADO);
        if (todoPagado) return EstadoPago.PAGADO;
        boolean tieneVencido = pagos.stream().anyMatch(p -> p.getEstado() == EstadoPago.VENCIDO);
        if (tieneVencido) return EstadoPago.VENCIDO;
        boolean tieneProximo = pagos.stream().anyMatch(p -> p.getEstado() == EstadoPago.PROXIMO_VENCER);
        if (tieneProximo) return EstadoPago.PROXIMO_VENCER;
        return EstadoPago.PENDIENTE;
    }
}
