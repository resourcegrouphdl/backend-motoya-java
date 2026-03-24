package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class ResultadoCalculoFinanciamiento {
    DatosCalculados datosCalculados;
    List<OpcionFinanciamiento> opciones;
    BigDecimal inicialMinimaCalculada;
}
