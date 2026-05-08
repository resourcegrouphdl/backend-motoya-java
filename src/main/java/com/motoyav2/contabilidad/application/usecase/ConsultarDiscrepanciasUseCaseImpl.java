package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.DiscrepanciaVoucher;
import com.motoyav2.contabilidad.domain.port.in.ConsultarDiscrepanciasUseCase;
import com.motoyav2.contabilidad.domain.port.out.VoucherAnalisisPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarDiscrepanciasUseCaseImpl implements ConsultarDiscrepanciasUseCase {

    private final VoucherAnalisisPort voucherAnalisisPort;

    @Override
    public Flux<DiscrepanciaVoucher> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId) {
        log.debug("Consultando discrepancias desde={} hasta={} tiendaId={}", desde, hasta, tiendaId);
        return voucherAnalisisPort.findDiscrepancias(desde, hasta, tiendaId)
                .onErrorResume(e -> {
                    log.error("Error consultando discrepancias: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
