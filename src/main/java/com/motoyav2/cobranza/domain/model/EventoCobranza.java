package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.TipoEventoCobranza;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventoCobranza {

    private String eventoId;
    private String contratoId;
    private TipoEventoCobranza tipo;
    private String agenteId;
    private String descripcion;
    private String timestamp;
    private Map<String, Object> payload;
}
