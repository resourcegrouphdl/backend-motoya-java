package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.*;
import com.motoyav2.finanzas.application.port.in.command.RegistrarPagoCommand;
import com.motoyav2.finanzas.application.port.in.command.SubirVoucherCommand;
import com.motoyav2.finanzas.application.port.out.FacturaPort;
import com.motoyav2.finanzas.application.port.out.VoucherStoragePort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaService implements ListarFacturasUseCase, ObtenerFacturaUseCase,
        RegistrarPagoUseCase, SubirVoucherUseCase {

    private final FacturaPort facturaPort;
    private final VoucherStoragePort voucherStoragePort;

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
        return facturaPort.findPagosByFacturaId(command.getFacturaId())
                .collectList()
                .flatMap(pagos -> {
                    PagoFactura pago = pagos.stream()
                            .filter(p -> p.getId().equals(command.getPagoId()))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Pago no encontrado"));

                    if (pago.getEstado() == EstadoPago.PAGADO) {
                        log.info("[Finanzas] Pago {} ya estaba registrado — idempotente", command.getPagoId());
                        return Mono.empty();
                    }

                    Map<String, Object> camposPago = Map.of(
                            "estado", EstadoPago.PAGADO.name(),
                            "fechaPago", command.getFechaPago().toString(),
                            "metodoPago", command.getMetodoPago().name(),
                            "actualizadoEn", Instant.now().toString()
                    );

                    List<PagoFactura> pagosActualizados = pagos.stream()
                            .map(p -> p.getId().equals(command.getPagoId())
                                    ? PagoFactura.builder().id(p.getId()).facturaId(p.getFacturaId())
                                        .numero(p.getNumero()).concepto(p.getConcepto())
                                        .monto(p.getMonto()).fechaProgramada(p.getFechaProgramada())
                                        .fechaPago(command.getFechaPago())
                                        .estado(EstadoPago.PAGADO)
                                        .voucherUrl(p.getVoucherUrl()).metodoPago(command.getMetodoPago()).build()
                                    : p)
                            .toList();

                    EstadoPago nuevoEstado = Factura.calcularEstado(pagosActualizados);
                    Map<String, Object> camposFactura = Map.of(
                            "estado", nuevoEstado.name(),
                            "_alertaActiva", nuevoEstado != EstadoPago.PAGADO,
                            "_tieneVencidos", nuevoEstado == EstadoPago.VENCIDO,
                            "actualizadoEn", Instant.now().toString()
                    );

                    return facturaPort.registrarPago(command.getFacturaId(), command.getPagoId(), camposPago)
                            .then(facturaPort.actualizarEstadoFactura(command.getFacturaId(), camposFactura));
                });
    }

    // ── SubirVoucherUseCase ───────────────────────────────────────────────

    @Override
    public Mono<String> ejecutar(SubirVoucherCommand command) {
        return voucherStoragePort.upload(command.getFacturaId(), command.getPagoId(), command.getArchivo())
                .flatMap(url -> facturaPort.actualizarVoucherUrl(
                        command.getFacturaId(), command.getPagoId(), url
                ).thenReturn(url));
    }
}
