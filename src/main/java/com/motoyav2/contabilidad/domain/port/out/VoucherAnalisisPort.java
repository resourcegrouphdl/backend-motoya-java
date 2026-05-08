package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.ConcentracionBancaria;
import com.motoyav2.contabilidad.domain.model.DiscrepanciaVoucher;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface VoucherAnalisisPort {

    /**
     * Retorna vouchers aprobados cuya diferencia |detectado - esperado| > 0.50.
     */
    Flux<DiscrepanciaVoucher> findDiscrepancias(LocalDate desde, LocalDate hasta, String tiendaId);

    /**
     * Agrupa los vouchers aprobados del período por banco y retorna la distribución.
     */
    Flux<ConcentracionBancaria> findConcentracionBancaria(LocalDate desde, LocalDate hasta, String tiendaId);
}
