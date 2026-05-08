package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class ResumenRecaudacion {

    LocalDate desde;
    LocalDate hasta;
    int totalPagos;
    Double montoTotal;
    Double promedioTicket;
    List<PuntoRecaudacion> puntos;
}
