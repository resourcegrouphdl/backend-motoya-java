package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.NumeradorPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.NumeradorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.NumeradorRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * Lee, incrementa y guarda el correlativo de la serie.
 * Nota: usa read-modify-write secuencial. En producción con alta concurrencia
 * considerar Firestore transaction nativa via SDK.
 */
@Component
@RequiredArgsConstructor
public class NumeradorPortAdapter implements NumeradorPort {

    private final NumeradorRepository repository;

    @Override
    public Mono<String> siguienteNumero(String serie) {
        return repository.findById(serie)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Serie de numeración no encontrada: " + serie)))
                .flatMap(doc -> {
                    long siguiente = doc.getUltimoNumero() + 1;
                    doc.setUltimoNumero(siguiente);
                    doc.setActualizadoEn(new Date());
                    return repository.save(doc)
                            .thenReturn(formatear(serie, siguiente));
                });
    }

    private String formatear(String serie, long numero) {
        return String.format("%s-%08d", serie, numero);
    }
}
