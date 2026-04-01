package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.*;
import com.motoyav2.finanzas.application.port.in.command.ConfirmarPagoComisionCommand;
import com.motoyav2.finanzas.application.port.out.ComisionPort;
import com.motoyav2.finanzas.application.port.out.PagoComisionPort;
import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.out.storage.FinanzasPdfStorageService;
import com.motoyav2.finanzas.infrastructure.pdf.ComprobanteComisionPdfService;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoComisionService implements
        ListarPagosComisionUseCase,
        ObtenerPagoComisionUseCase,
        ConfirmarPagoComisionUseCase,
        GenerarPagosQuincenalesUseCase {

    private final PagoComisionPort             pagoPort;
    private final ComisionPort                 comisionPort;
    private final ComprobanteComisionPdfService pdfService;
    private final FinanzasPdfStorageService    storageService;
    private final NotificationFacade           notificationFacade;

    @Override
    public Flux<PagoComisionVendedor> ejecutar(String vendedorId, String tiendaId, String estado) {
        return pagoPort.findAll(vendedorId, tiendaId, estado);
    }

    @Override
    public Mono<PagoComisionVendedor> ejecutar(String id) {
        return pagoPort.findById(id);
    }

    @Override
    public Mono<Void> ejecutar(ConfirmarPagoComisionCommand command) {
        return pagoPort.confirmar(command)
                .doOnSuccess(v -> {
                    log.info("[PagoComision] Confirmado — pagoId={}", command.getPagoId());
                    // Generación de comprobante PDF + notificación de forma asíncrona
                    // (no bloquea la respuesta al admin)
                    generarComprobanteYNotificarAsync(command.getPagoId());
                });
    }

    @Override
    public Mono<Integer> ejecutar() {
        return pagoPort.generarPagosQuincenales()
                .doOnSuccess(n -> log.info("[PagoComision] Pagos quincenales generados: {}", n));
    }

    // ── Flujo async: PDF → GCS → Firestore → WhatsApp + Email ────────────────

    private void generarComprobanteYNotificarAsync(String pagoId) {
        pagoPort.findById(pagoId)
                .flatMap(pago ->
                        comisionPort.findByPagoId(pagoId).collectList()
                                .flatMap(comisiones -> pdfService.generar(pago, comisiones))
                                .flatMap(pdfBytes -> storageService.subirPdf(
                                        "finanzas/comprobantes-comision/" + pagoId + ".pdf",
                                        pdfBytes))
                                .flatMap(url -> pagoPort.actualizarComprobanteUrl(pagoId, url)
                                        .thenReturn(url))
                                .flatMap(url -> notificationFacade
                                        .notificarPagoComisionConfirmado(
                                                pago.getId(),                                    // referenceId — nunca null
                                                nvl(pago.getVendedorEmail()),
                                                nvl(pago.getVendedorPhone()),
                                                nvl(pago.getVendedorNombre()),
                                                nvl(pago.getPeriodoDesde()) + " al " + nvl(pago.getPeriodoHasta()),
                                                pago.getMontoTotal() != null
                                                        ? pago.getMontoTotal().toPlainString()
                                                        : "0",
                                                url
                                        ))
                )
                .onErrorResume(e -> {
                    log.error("[PagoComision] Error generando comprobante async pagoId={}: {}",
                            pagoId, e.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private String nvl(String val) {
        return val != null ? val : "";
    }
}
