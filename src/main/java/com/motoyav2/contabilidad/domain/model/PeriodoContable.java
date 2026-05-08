package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class PeriodoContable {

    LocalDate desde;
    LocalDate hasta;

    public static PeriodoContable of(LocalDate desde, LocalDate hasta) {
        return PeriodoContable.builder()
                .desde(desde)
                .hasta(hasta)
                .build();
    }
}
