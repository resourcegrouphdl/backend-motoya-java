package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.ConcentracionBancaria;
import com.motoyav2.contabilidad.domain.port.in.ConsultarConcentracionBancariaUseCase;
import com.motoyav2.contabilidad.domain.port.out.VoucherAnalisisPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarConcentracionBancariaUseCaseImpl implements ConsultarConcentracionBancariaUseCase {

    private final VoucherAnalisisPort voucherAnalisisPort;

    @Override
    public Flux<ConcentracionBancaria> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId) {
        log.debug("Consultando concentracion bancaria desde={} hasta={} tiendaId={}", desde, hasta, tiendaId);
        return voucherAnalisisPort.findConcentracionBancaria(desde, hasta, tiendaId)
                .onErrorResume(e -> {
                    log.error("Error consultando concentracion bancaria: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
