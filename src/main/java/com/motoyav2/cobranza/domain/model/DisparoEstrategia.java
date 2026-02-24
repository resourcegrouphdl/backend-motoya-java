package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.EstadoDisparoEstrategia;
import com.motoyav2.cobranza.domain.enums.ResultadoDisparo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DisparoEstrategia {

    private String disparoId;
    private String estrategiaId;
    private String contratoId;
    private String plantillaId;
    private String mensajeId;

    private EstadoDisparoEstrategia estado;
    private ResultadoDisparo resultado;

    private String programadoPara;
    private String ejecutadoEn;
    private String errorMensaje;
}
