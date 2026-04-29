package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.AjustarFinanciamientoCommand;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.OpcionFinanciamiento;
import com.motoyav2.evaluacion.domain.model.ResultadoCalculoFinanciamiento;
import com.motoyav2.evaluacion.domain.port.in.AjustarFinanciamientoUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.service.CalculadoraFinanciamientoService;
import com.motoyav2.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AjustarFinanciamientoUseCaseImpl implements AjustarFinanciamientoUseCase {

    private final SolicitudRepository solicitudRepository;

    private static final List<Integer> PLAZOS_VALIDOS =
            CalculadoraFinanciamientoService.getOpcionesQuincenas();

    @Override
    public Mono<Map<String, Object>> ejecutar(AjustarFinanciamientoCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {

                    // ── Precio efectivo: usar el nuevo si viene, si no el almacenado ──
                    BigDecimal precioOriginal = solicitud.getDatosFinancieros() != null
                            ? solicitud.getDatosFinancieros().getMontoVehiculo()
                            : solicitud.getPrecioCompraMoto();
                    BigDecimal precio = command.nuevoPrecioMoto() != null
                            ? command.nuevoPrecioMoto()
                            : precioOriginal;
                    boolean cambiaPrecio = command.nuevoPrecioMoto() != null
                            && command.nuevoPrecioMoto().compareTo(precioOriginal) != 0;

                    // ── Validaciones de negocio ───────────────────────────────
                    if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new BadRequestException(
                                "El precio de la moto debe ser mayor a cero"));
                    }
                    if (command.nuevaInicial().compareTo(BigDecimal.ZERO) < 0) {
                        return Mono.error(new BadRequestException(
                                "La inicial no puede ser negativa"));
                    }
                    if (!PLAZOS_VALIDOS.contains(command.nuevoPlazo())) {
                        return Mono.error(new BadRequestException(
                                "El plazo debe ser 16, 20 o 24 quincenas"));
                    }
                    BigDecimal costoTotal = CalculadoraFinanciamientoService.calcularPrecioTotal(precio);
                    if (command.nuevaInicial().compareTo(costoTotal) >= 0) {
                        return Mono.error(new BadRequestException(
                                "La inicial no puede ser igual o mayor al costo total"));
                    }

                    // ── Recálculo con calculadora simplificada (interés lineal) ─
                    ResultadoCalculoFinanciamiento calc;
                    try {
                        calc = CalculadoraFinanciamientoService
                                .calcularFinanciamientoCompleto(precio, command.nuevaInicial());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new BadRequestException(e.getMessage()));
                    }

                    OpcionFinanciamiento opcion = calc.getOpciones().stream()
                            .filter(op -> op.getQuincenas() == command.nuevoPlazo())
                            .findFirst()
                            .orElseThrow(() -> new BadRequestException("Plazo no disponible"));

                    BigDecimal cuota          = opcion.getCuotaQuincenal();
                    BigDecimal montoFinanciar  = calc.getDatosCalculados().getMontoFinanciar();
                    BigDecimal total           = opcion.getSumaTotal();
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

                    // Precio legacy — se actualiza solo cuando el precio cambió
                    if (cambiaPrecio) {
                        updates.put("precioCompraMoto", precio.doubleValue());
                    }

                    if (solicitud.getDatosFinancieros() != null) {
                        Map<String, Object> df = new HashMap<>();
                        df.put("inicial",                  command.nuevaInicial().doubleValue());
                        df.put("montoCuotaQuincenal",       cuota.doubleValue());
                        df.put("numeroCuotasQuincenales",   command.nuevoPlazo());
                        df.put("montoFinanciar",            montoFinanciar.doubleValue());
                        df.put("totalAPagar",               total.doubleValue());
                        df.put("porcentajeInicial",         pct.doubleValue());
                        df.put("interesTotal",              opcion.getInteresTotal().doubleValue());
                        df.put("tasaLineal",                opcion.getTasa().doubleValue());
                        df.put("modoCalculadora",           "SIMPLIFICADO");
                        // Precio y costo total — se actualizan solo cuando el precio cambió
                        if (cambiaPrecio) {
                            df.put("montoVehiculo", precio.doubleValue());
                            df.put("costoTotal",    costoTotal.doubleValue());
                        }
                        updates.put("datosFinancieros", df);
                    }

                    Map<String, Object> resultado = Map.of(
                            "inicial",                 command.nuevaInicial().doubleValue(),
                            "montoFinanciar",          montoFinanciar.doubleValue(),
                            "numeroCuotasQuincenales", command.nuevoPlazo(),
                            "montoCuotaQuincenal",     cuota.doubleValue(),
                            "totalAPagar",             total.doubleValue(),
                            "porcentajeInicial",       pct.doubleValue()
                    );

                    return solicitudRepository.updateFields(command.solicitudId(), updates)
                            .thenReturn(resultado);
                });
    }
}
