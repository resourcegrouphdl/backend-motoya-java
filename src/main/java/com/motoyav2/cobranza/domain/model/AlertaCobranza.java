package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.NivelAlerta;
import com.motoyav2.cobranza.domain.enums.TipoAlerta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertaCobranza {

    private String alertaId;
    private String contratoId;
    private String agenteId;
    private String storeId;

    private TipoAlerta tipo;
    private NivelAlerta nivel;
    private String titulo;
    private String descripcion;

    private Boolean leida;
    private Boolean descartada;

    /** ISO-8601 — Firestore TTL policy aplica sobre el campo `expiraEn` */
    private String expiraEn;
    private String createdAt;
}
