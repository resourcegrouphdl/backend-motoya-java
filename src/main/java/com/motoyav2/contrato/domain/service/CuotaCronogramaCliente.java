package com.motoyav2.contrato.domain.service;

import com.motoyav2.contrato.domain.enums.EstadoDePago;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CuotaCronogramaCliente {

  private static final int DIAS_PRIMER_PAGO = 35;

  public List<CuotaCronograma> generarCronograma(
      Instant fechaFacturacionInstant,
      BigDecimal montoCuota,
      int numeroCuotas
  ) {
    ZoneId zone = ZoneId.of("America/Lima");

    LocalDate fechaFacturacion = fechaFacturacionInstant
        .atZone(zone)
        .toLocalDate();

    // Primer pago = +35 días
    LocalDate primerPago = fechaFacturacion.plusDays(35);
    LocalDate segundoPago = primerPago.plusDays(15);

    int dia1 = primerPago.getDayOfMonth();
    int dia2 = segundoPago.getDayOfMonth();

    BigDecimal saldoPendiente = montoCuota.multiply(BigDecimal.valueOf(numeroCuotas));

    List<CuotaCronograma> cronograma = new ArrayList<>();
    LocalDate fechaCuota = primerPago;

    for (int i = 1; i <= numeroCuotas; i++) {

      saldoPendiente = saldoPendiente.subtract(montoCuota);

      cronograma.add(
          CuotaCronograma.builder()
              .numeroCuota(i)
              .fechaVencimiento(fechaCuota.atStartOfDay(zone).toInstant())
              .montoCuota(montoCuota)
              .montoCapital(montoCuota)
              .montoInteres(BigDecimal.ZERO)
              .saldoPendiente(saldoPendiente.max(BigDecimal.ZERO))
              .estadoPago(EstadoDePago.PENDIENTE)
              .fechaPago(null)
              .montoPagado(BigDecimal.ZERO)
              .diasMora(0)
              .montoMora(BigDecimal.ZERO)
              .build()
      );

      fechaCuota = siguienteFechaPatron(fechaCuota, dia1, dia2);
    }

    return cronograma;
  }

  private LocalDate siguienteFechaPatron(LocalDate fechaActual, int dia1, int dia2) {

    int diaActual = fechaActual.getDayOfMonth();

    if (diaActual == dia1) {
      return ajustarDia(fechaActual, dia2);
    } else {
      return ajustarDia(fechaActual.plusMonths(1), dia1);
    }
  }

  private LocalDate ajustarDia(LocalDate base, int diaObjetivo) {
    int ultimoDiaMes = base.lengthOfMonth();
    int diaFinal = Math.min(diaObjetivo, ultimoDiaMes);
    return base.withDayOfMonth(diaFinal);
  }
}
