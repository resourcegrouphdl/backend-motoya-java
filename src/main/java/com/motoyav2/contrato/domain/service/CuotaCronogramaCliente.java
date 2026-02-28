package com.motoyav2.contrato.domain.service;

import com.motoyav2.contrato.domain.enums.EstadoDePago;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el cronograma de cuotas quincenales con el siguiente patrón:
 *
 *  - Primer pago  : fechaFacturacion + 35 días  → extrae dia1
 *  - Segundo pago : primerPago + 15 días         → extrae dia2
 *  - Patrón       : dia1/mesN, dia2/mesN, dia1/mesN+1, dia2/mesN+1 …
 *
 * Si un mes tiene menos días que dia1 o dia2 se usa el último día del mes.
 * Si dia2 < dia1 (el +15 cruzó el cambio de mes) los pagos de dia2
 * caen en el mes siguiente a los de dia1.
 *
 * Ejemplo A — dia1=4, dia2=19 (mismo mes):
 *   4-ene, 19-ene, 4-feb, 19-feb, 4-mar …
 *
 * Ejemplo B — dia1=30, dia2=14 (dia2 en mes siguiente):
 *   30-ene, 14-feb, 28-feb*, 14-mar, 30-mar, 14-abr …
 *   (* febrero ajustado al día máximo disponible)
 */
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

    LocalDate fechaFacturacion = fechaFacturacionInstant.atZone(zone).toLocalDate();

    LocalDate primerPago  = fechaFacturacion.plusDays(DIAS_PRIMER_PAGO);
    LocalDate segundoPago = primerPago.plusDays(15);

    int dia1 = primerPago.getDayOfMonth();
    int dia2 = segundoPago.getDayOfMonth();

    // Cuando dia2 < dia1 el segundo pago cruzó al mes siguiente
    int offsetMesDia2 = dia2 < dia1 ? 1 : 0;

    // Mes base calculado desde YearMonth para no depender del día concreto
    YearMonth mesBase = YearMonth.from(primerPago);

    log.info("[Cronograma] dia1={}, dia2={}, offsetMesDia2={}, cuotas={}",
        dia1, dia2, offsetMesDia2, numeroCuotas);

    BigDecimal saldoPendiente = montoCuota.multiply(BigDecimal.valueOf(numeroCuotas));
    List<CuotaCronograma> cronograma = new ArrayList<>();

    for (int i = 1; i <= numeroCuotas; i++) {

      saldoPendiente = saldoPendiente.subtract(montoCuota);

      /*
       * ciclo 0 → cuotas 1 y 2
       * ciclo 1 → cuotas 3 y 4
       * ciclo N → cuotas 2N+1 y 2N+2
       */
      int ciclo = (i - 1) / 2;

      LocalDate fechaCuota;
      if (i % 2 == 1) {
        // Posición dia1: avanza un mes por ciclo completo
        fechaCuota = fechaPatron(mesBase.plusMonths(ciclo), dia1);
      } else {
        // Posición dia2: mismo ciclo + offset si dia2 cruzó al mes siguiente
        fechaCuota = fechaPatron(mesBase.plusMonths(ciclo + offsetMesDia2), dia2);
      }

      log.debug("[Cronograma] cuota={} → {}", i, fechaCuota);

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
    }

    return cronograma;
  }

  /**
   * Devuelve la fecha para el mes indicado usando {@code diaObjetivo},
   * pero si el mes tiene menos días usa el último día disponible.
   *
   * Ej: mes=febrero, diaObjetivo=30 → 28 (o 29 en bisiesto)
   */
  private LocalDate fechaPatron(YearMonth mes, int diaObjetivo) {
    int dia = Math.min(diaObjetivo, mes.lengthOfMonth());
    return LocalDate.of(mes.getYear(), mes.getMonth(), dia);
  }
}
