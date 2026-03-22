package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.AjustarFinanciamientoCommand;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.port.in.AjustarFinanciamientoUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.financiamiento.domain.model.ResultadoSimulacion;
import com.motoyav2.financiamiento.domain.model.SolicitudSimulacion;
import com.motoyav2.financiamiento.domain.service.MotorFinancieroService;
import com.motoyav2.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AjustarFinanciamientoUseCaseImpl implements AjustarFinanciamientoUseCase {

    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<Map<String, Object>> ejecutar(AjustarFinanciamientoCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {

                    // ── Valores originales ────────────────────────────────────
                    BigDecimal originalInicial = solicitud.getDatosFinancieros() != null
                            ? solicitud.getDatosFinancieros().getInicial()
                            : solicitud.getInicial();
                    int originalPlazo = solicitud.getDatosFinancieros() != null
                            ? solicitud.getDatosFinancieros().getNumeroCuotasQuincenales()
                            : solicitud.getPlazoQuincenas();
                    BigDecimal precio = solicitud.getDatosFinancieros() != null
                            ? solicitud.getDatosFinancieros().getMontoVehiculo()
                            : solicitud.getPrecioCompraMoto();

                    // ── Validaciones de negocio ───────────────────────────────
                    if (command.nuevaInicial().compareTo(originalInicial) < 0) {
                        return Mono.error(new BadRequestException(
                                "La inicial ajustada no puede ser menor a la original (S/ "
                                        + originalInicial.setScale(2, RoundingMode.HALF_UP) + ")"));
                    }
                    if (command.nuevoPlazo() > originalPlazo) {
                        return Mono.error(new BadRequestException(
                                "El plazo ajustado no puede ser mayor al original ("
                                        + originalPlazo + " quincenas)"));
                    }
                    if (command.nuevoPlazo() < 4) {
                        return Mono.error(new BadRequestException("El plazo mínimo es 4 quincenas"));
                    }
                    BigDecimal costoTotal = precio.add(MotorFinancieroService.GASTOS_ADMINISTRATIVOS_DEFAULT);
                    if (command.nuevaInicial().compareTo(costoTotal) >= 0) {
                        return Mono.error(new BadRequestException(
                                "La inicial no puede ser igual o mayor al costo total"));
                    }

                    // ── Recálculo con motor financiero (amortización francesa) ─
                    BigDecimal tea = command.tea() != null
                            ? command.tea()
                            : MotorFinancieroService.TEA_DEFAULT;

                    ResultadoSimulacion sim;
                    try {
                        sim = MotorFinancieroService.simular(SolicitudSimulacion.builder()
                                .precioVehiculo(precio)
                                .cuotaInicial(command.nuevaInicial())
                                .numeroCuotas(command.nuevoPlazo())
                                .tea(tea)
                                .build());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new BadRequestException(e.getMessage()));
                    }

                    BigDecimal cuota         = sim.getCuotaQuincenal();
                    BigDecimal montoFinanciar = sim.getMontoFinanciado();
                    BigDecimal total          = sim.getTotalPagar();
                    BigDecimal pct = costoTotal.compareTo(BigDecimal.ZERO) > 0
                            ? command.nuevaInicial()
                                    .divide(costoTotal, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    // ── Construir mapa de actualización parcial ───────────────
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("inicial", command.nuevaInicial().doubleValue());
                    updates.put("montoCuota", cuota.doubleValue());
                    updates.put("plazoQuincenas", command.nuevoPlazo());
                    updates.put("updatedAt", Timestamp.now());

                    if (solicitud.getDatosFinancieros() != null) {
                        Map<String, Object> df = new HashMap<>();
                        df.put("inicial", command.nuevaInicial().doubleValue());
                        df.put("montoCuotaQuincenal", cuota.doubleValue());
                        df.put("numeroCuotasQuincenales", command.nuevoPlazo());
                        df.put("montoFinanciar", montoFinanciar.doubleValue());
                        df.put("totalAPagar", total.doubleValue());
                        df.put("porcentajeInicial", pct.doubleValue());
                        df.put("tea", tea.doubleValue());
                        df.put("tcea", sim.getTcea().doubleValue());
                        df.put("tasaQuincenal", sim.getTasaQuincenal().doubleValue());
                        updates.put("datosFinancieros", df);
                    }

                    Map<String, Object> resultado = Map.of(
                            "inicial", command.nuevaInicial().doubleValue(),
                            "montoFinanciar", montoFinanciar.doubleValue(),
                            "numeroCuotasQuincenales", command.nuevoPlazo(),
                            "montoCuotaQuincenal", cuota.doubleValue(),
                            "totalAPagar", total.doubleValue(),
                            "porcentajeInicial", pct.doubleValue(),
                            "tea", tea.doubleValue(),
                            "tcea", sim.getTcea().doubleValue()
                    );

                    return solicitudRepository.updateFields(command.solicitudId(), updates)
                            .thenReturn(resultado);
                });
    }
}
