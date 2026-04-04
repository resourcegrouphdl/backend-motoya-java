package com.motoyav2.finanzas.infrastructure.pdf;

import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Genera el PDF comprobante de pago de comisión a vendedor.
 * Agrupa por vendedor: una página por pago quincena.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteComisionPdfService {

    private static final String TEMPLATE = "finanzas/comprobante-comision";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FinanzasPdfRenderer renderer;

    public Mono<byte[]> generar(PagoComisionVendedor pago, List<ComisionVendedor> comisiones) {
        return Mono.fromCallable(() -> {
            Map<String, Object> vars = Map.of(
                    "pago",       pago,
                    "comisiones", comisiones,
                    "fechaEmision", LocalDate.now().format(FMT),
                    "totalMonto", pago.getMontoTotal(),
                    "totalVentas", pago.getTotalVentas()
            );
            byte[] bytes = renderer.render(TEMPLATE, vars);
            log.info("[ComprobanteComision] PDF generado pagoId={} bytes={}", pago.getId(), bytes.length);
            return bytes;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
