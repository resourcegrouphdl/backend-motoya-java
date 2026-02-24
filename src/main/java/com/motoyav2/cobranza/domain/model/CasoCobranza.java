package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CasoCobranza {

    private String contratoId;
    private DatosTitular titular;
    private String agenteId;
    private String storeId;

    private NivelEstrategia nivelEstrategia;
    private EstadoCaso estado;
    private CicloVidaCaso cicloVida;
    private ExcepcionCaso excepcion;

    private Integer diasMora;
    private Double montoPendiente;
    private Double montoRecuperado;

    private String ultimoContacto;
    private String fechaAsignacion;
    private String fechaActualizacion;

    private List<CuotaCronograma> cronograma;
}
