package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.EstadoAcuerdo;
import com.motoyav2.cobranza.domain.enums.TipoAcuerdo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Acuerdo {

    private String acuerdoId;
    private String contratoId;
    private String agenteId;

    private TipoAcuerdo tipo;
    private EstadoAcuerdo estado;

    private Double montoTotal;
    private Double tasaDescuento;
    private Integer cantidadCuotas;
    private List<CuotaAcuerdo> cuotas;

    private String createdAt;
    private String updatedAt;
}
