package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.MensajeWhatsappRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MensajeWhatsappPortAdapter implements MensajeWhatsappPort {

    private final MensajeWhatsappRepository repository;

    @Override
    public Mono<MensajeWhatsappDocument> save(MensajeWhatsappDocument mensaje) {
        return repository.save(mensaje);
    }

    @Override
    public Mono<MensajeWhatsappDocument> findByWamid(String wamid) {
        return repository.findByWamid(wamid);
    }

    @Override
    public Flux<MensajeWhatsappDocument> findByContratoId(String contratoId) {
        return repository.findByContratoId(contratoId);
    }
}
