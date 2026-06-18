package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.*;
import com.motoyav2.finanzas.application.port.in.command.RegistrarPagoCommand;
import com.motoyav2.finanzas.application.port.in.command.SubirVoucherCommand;
import com.motoyav2.finanzas.application.port.out.FacturaPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.enums.TipoConceptoPago;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaService implements ListarFacturasUseCase, ObtenerFacturaUseCase,
        RegistrarPagoUseCase, SubirVoucherUseCase {

    private final FacturaPort facturaPort;
    private final DocumentAiService documentAiService;
    private final NotificationFacade notificationFacade;

    @Value("${app.notifications.cobranzas.enabled:false}")
    private boolean notificacionesEnabled;

    // ── ListarFacturasUseCase ──────────────────────────────────────────────

    @Override
    public Flux<Factura> ejecutar(FiltrosFacturaRequest filtros) {
        return facturaPort.findAll(filtros);
    }

    // ── ObtenerFacturaUseCase ─────────────────────────────────────────────

    @Override
    public Mono<Factura> ejecutar(String facturaId) {
        return facturaPort.findById(facturaId)
                .switchIfEmpty(Mono.error(new NotFoundException("Factura no encontrada")));
    }

    // ── RegistrarPagoUseCase ──────────────────────────────────────────────

    @Override
    public Mono<Void> ejecutar(RegistrarPagoCommand command) {
        // findById ya incluye la subcoleción de pagos (via enrichWithPagos)
        return facturaPort.findById(command.getFacturaId())
                .switchIfEmpty(Mono.error(new NotFoundException("Factura no encontrada")))
                .flatMap(factura -> {
                    List<PagoFactura> pagos = factura.getPagos();

                    PagoFactura pago = pagos.stream()
                            .filter(p -> p.getId().equals(command.getPagoId()))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Pago no encontrado"));

                    if (pago.getEstado() == EstadoPago.PAGADO) {
                        log.info("[Finanzas] Pago {} ya estaba registrado — idempotente", command.getPagoId());
                        return Mono.empty();
                    }

                    Map<String, Object> camposPago = Map.of(
                            "estado",       EstadoPago.PAGADO.name(),
                            "fechaPago",    command.getFechaPago().toString(),
                            "metodoPago",   command.getMetodoPago().name(),
                            "actualizadoEn", Instant.now().toString()
                    );

                    List<PagoFactura> pagosActualizados = pagos.stream()
                            .map(p -> p.getId().equals(command.getPagoId())
                                    ? PagoFactura.builder()
                                        .id(p.getId()).facturaId(p.getFacturaId())
                                        .numero(p.getNumero()).concepto(p.getConcepto())
                                        .monto(p.getMonto()).fechaProgramada(p.getFechaProgramada())
                                        .fechaPago(command.getFechaPago())
                                        .estado(EstadoPago.PAGADO)
                                        .voucherUrl(p.getVoucherUrl()).metodoPago(command.getMetodoPago())
                                        .build()
                                    : p)
                            .toList();

                    EstadoPago nuevoEstado = Factura.calcularEstado(pagosActualizados);
                    Map<String, Object> camposFactura = Map.of(
                            "estado",        nuevoEstado.name(),
                            "alertaActiva",  nuevoEstado != EstadoPago.PAGADO,
                            "tieneVencidos", nuevoEstado == EstadoPago.VENCIDO,
                            "actualizadoEn", Instant.now().toString()
                    );

                    return facturaPort.registrarPago(command.getFacturaId(), command.getPagoId(), camposPago)
                            .then(facturaPort.actualizarEstadoFactura(command.getFacturaId(), camposFactura))
                            .doOnSuccess(v -> notificarPagoAsync(factura, pago, command));
                });
    }

    // ── SubirVoucherUseCase ───────────────────────────────────────────────

    @Override
    public Mono<String> ejecutar(SubirVoucherCommand command) {
        Map<String, Object> camposVoucher = new java.util.HashMap<>();
        camposVoucher.put("voucherUrl", command.getVoucherUrl());
        camposVoucher.put("documentAiStatus", "PENDIENTE");
        if (command.getGcsPath() != null) {
            camposVoucher.put("voucherGcsPath", command.getGcsPath());
        }

        return facturaPort.registrarPago(command.getFacturaId(), command.getPagoId(), camposVoucher)
                .doOnSuccess(v -> {
                    if (command.getGcsPath() != null) {
                        documentAiService.extraerAsync(
                                command.getFacturaId(),
                                command.getPagoId(),
                                command.getGcsPath(),
                                command.getMimeType()
                        );
                    }
                })
                .thenReturn(command.getVoucherUrl());
    }

    // ── Notificación async (fire-and-forget) ─────────────────────────────

    private void notificarPagoAsync(Factura factura, PagoFactura pago, RegistrarPagoCommand command) {
        if (!notificacionesEnabled) {
            log.info("[Finanzas] Notificación WhatsApp desactivada (app.notifications.cobranzas.enabled=false) — factura {}", factura.getId());
            return;
        }
        String conceptoLabel = (pago.getConcepto() == TipoConceptoPago.INICIAL)
                ? "Pago Inicial" : "Pago de Saldo";
        String montoStr = "S/ " + (pago.getMonto() != null
                ? pago.getMonto().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2));

        notificationFacade.notificarPagoFacturaTienda(
                factura.getId(),
                factura.getTiendaEmail(),
                factura.getTiendaTelefono(),
                factura.getTiendaNombre(),
                factura.getNumero(),
                factura.getClienteNombre(),
                conceptoLabel,
                montoStr,
                command.getFechaPago().toString(),
                command.getMetodoPago().name()
        )
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
                null,
                err -> log.warn("[Finanzas] Error al notificar pago factura {}: {}", factura.getId(), err.getMessage())
        );
    }
}
