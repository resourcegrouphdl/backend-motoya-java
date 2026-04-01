package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.dto.PromesaResumenDto;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Listar promesas globales (todos los contratos) con datos del caso enriquecidos.
 * Filtro por storeId para supervisores; sin filtro = admin ve todo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromesaGlobalService {

    private final CasoCobranzaPort casoPort;
    private final PromesaPort promesaPort;

    public Flux<PromesaResumenDto> listar(String storeId, String estado) {
        Flux<?> casos = (storeId != null && !storeId.isBlank())
                ? casoPort.findByStoreId(storeId)
                : casoPort.findAll();

        return casos
                .cast(com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument.class)
                .filter(c -> !"CERRADO".equals(c.getCicloVida()) && !"PAGADO_TOTAL".equals(c.getCicloVida()))
                .flatMap(caso ->
                        promesaPort.findByContratoId(caso.getContratoId())
                                .filter(p -> estado == null || estado.isBlank() || estado.equalsIgnoreCase(p.getEstado()))
                                .map(p -> new PromesaResumenDto(
                                        p.getId(),
                                        caso.getContratoId(),
                                        caso.getClienteNombre(),
                                        caso.getSaldoActual(),
                                        p.getFecha(),
                                        p.getMonto(),
                                        p.getEstado(),
                                        p.getObservaciones()
                                ))
                );
    }
}
