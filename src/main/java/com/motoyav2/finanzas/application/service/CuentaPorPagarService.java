package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.*;
import com.motoyav2.finanzas.application.port.in.command.CrearCuentaCommand;
import com.motoyav2.finanzas.application.port.out.CuentaPorPagarPort;
import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import com.motoyav2.finanzas.domain.model.CuotaCuenta;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaPorPagarService implements ListarCuentasUseCase, CrearCuentaUseCase,
        PagarCuentaUseCase, PagarCuotaUseCase {

    private final CuentaPorPagarPort cuentaPort;

    // ── ListarCuentasUseCase ──────────────────────────────────────────────

    @Override
    public Flux<CuentaPorPagar> ejecutar(TipoCuenta tipo, EstadoCuenta estado) {
        return cuentaPort.findAll(tipo, estado);
    }

    // ── CrearCuentaUseCase ────────────────────────────────────────────────

    @Override
    public Mono<CuentaPorPagar> ejecutar(CrearCuentaCommand cmd) {
        List<CuotaCuenta> cuotas = generarCuotas(cmd);
        LocalDate fechaVencimiento = cmd.getFechaVencimiento();
        String cuentaId = generarId("CTA");

        CuentaPorPagar cuenta = CuentaPorPagar.builder()
                .id(cuentaId)
                .tipo(cmd.getTipo())
                .proveedor(cmd.getProveedor())
                .descripcion(cmd.getDescripcion())
                .numeroDocumento(cmd.getNumeroDocumento())
                .montoTotal(cmd.getMontoTotal())
                .numeroCuotas(cmd.getNumeroCuotas())
                .estado(EstadoCuenta.PENDIENTE)
                .fechaVencimiento(fechaVencimiento)
                .creadoEn(LocalDateTime.now())
                .cuotas(cuotas)
                .build();

        return cuentaPort.save(cuenta, cuotas);
    }

    // ── PagarCuentaUseCase (pago único) ───────────────────────────────────

    @Override
    public Mono<Void> ejecutar(String cuentaId) {
        return cuentaPort.findCuotasByCuentaId(cuentaId)
                .collectList()
                .flatMap(cuotas -> {
                    if (cuotas.size() != 1) {
                        return Mono.error(new BadRequestException(
                                "Use el endpoint de cuota individual para cuentas con múltiples cuotas"));
                    }
                    CuotaCuenta cuota = cuotas.get(0);
                    if (cuota.getEstado() == EstadoCuenta.PAGADO) {
                        return Mono.empty();
                    }
                    String hoy = LocalDate.now().toString();
                    return cuentaPort.actualizarCuota(cuentaId, cuota.getId(), Map.of(
                                    "estado", EstadoCuenta.PAGADO.name(),
                                    "fechaPago", hoy,
                                    "actualizadoEn", Instant.now().toString()))
                            .then(cuentaPort.actualizarCuenta(cuentaId, Map.of(
                                    "estado", EstadoCuenta.PAGADO.name(),
                                    "_alertaActiva", false,
                                    "_tieneVencidos", false,
                                    "actualizadoEn", Instant.now().toString())));
                });
    }

    // ── PagarCuotaUseCase ─────────────────────────────────────────────────

    @Override
    public Mono<Void> ejecutar(String cuentaId, String cuotaId) {
        return cuentaPort.findCuotasByCuentaId(cuentaId)
                .collectList()
                .flatMap(cuotas -> {
                    CuotaCuenta cuota = cuotas.stream()
                            .filter(c -> c.getId().equals(cuotaId))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Cuota no encontrada"));

                    if (cuota.getEstado() == EstadoCuenta.PAGADO) {
                        return Mono.empty();
                    }

                    String hoy = LocalDate.now().toString();
                    List<CuotaCuenta> actualizadas = cuotas.stream()
                            .map(c -> c.getId().equals(cuotaId)
                                    ? CuotaCuenta.builder().id(c.getId()).cuentaId(c.getCuentaId())
                                        .numero(c.getNumero()).monto(c.getMonto())
                                        .fechaVencimiento(c.getFechaVencimiento())
                                        .fechaPago(LocalDate.now()).estado(EstadoCuenta.PAGADO).build()
                                    : c)
                            .toList();

                    EstadoCuenta nuevoEstado = CuentaPorPagar.calcularEstado(actualizadas);
                    LocalDate nuevaFechaVenc = CuentaPorPagar.calcularFechaVencimiento(actualizadas);

                    Map<String, Object> camposCuenta = new java.util.HashMap<>(Map.of(
                            "estado", nuevoEstado.name(),
                            "_alertaActiva", nuevoEstado != EstadoCuenta.PAGADO,
                            "_tieneVencidos", nuevoEstado == EstadoCuenta.VENCIDO,
                            "actualizadoEn", Instant.now().toString()
                    ));
                    if (nuevaFechaVenc != null) {
                        camposCuenta.put("fechaVencimiento", nuevaFechaVenc.toString());
                    }

                    return cuentaPort.actualizarCuota(cuentaId, cuotaId, Map.of(
                                    "estado", EstadoCuenta.PAGADO.name(),
                                    "fechaPago", hoy,
                                    "actualizadoEn", Instant.now().toString()))
                            .then(cuentaPort.actualizarCuenta(cuentaId, camposCuenta));
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<CuotaCuenta> generarCuotas(CrearCuentaCommand cmd) {
        int n = cmd.getNumeroCuotas();
        BigDecimal montoPorCuota = cmd.getMontoTotal()
                .divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal residuo = cmd.getMontoTotal().subtract(montoPorCuota.multiply(BigDecimal.valueOf(n)));

        List<CuotaCuenta> cuotas = new ArrayList<>();
        String cuentaId = generarId("CTA");

        for (int i = 1; i <= n; i++) {
            BigDecimal monto = (i == n) ? montoPorCuota.add(residuo) : montoPorCuota;
            LocalDate fecha = cmd.getFechaVencimiento().plusMonths(i - 1L);
            cuotas.add(CuotaCuenta.builder()
                    .id(cuentaId + "-C" + i)
                    .cuentaId(cuentaId)
                    .numero(i)
                    .monto(monto)
                    .fechaVencimiento(fecha)
                    .estado(EstadoCuenta.PENDIENTE)
                    .build());
        }
        return cuotas;
    }

    private String generarId(String prefijo) {
        String fecha = LocalDate.now().toString().replace("-", "");
        return prefijo + "-" + fecha + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
