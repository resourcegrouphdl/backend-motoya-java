package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.dto.EventoCalendarioParseado;
import com.motoyav2.cobranza.application.dto.ImportarCalendarioResultDto;
import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.domain.NivelMoraCalculadora;
import com.motoyav2.cobranza.infrastructure.adapter.out.calendar.GoogleCalendarService;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportarCalendarioService {

    private final GoogleCalendarService calendarService;
    private final IniciarCasoUseCase iniciarCasoUseCase;

    public Mono<ImportarCalendarioResultDto> importar(String calendarId, String storeId,
                                                       String agenteAsignadoId, String agenteNombre,
                                                       String usuarioId) {
        return calendarService.obtenerEventos(calendarId)
                .collectMultimap(EventoCalendarioParseado::nombreCompleto)
                .flatMap(grouped -> {
                    AtomicInteger creados = new AtomicInteger(0);
                    AtomicInteger errores = new AtomicInteger(0);

                    List<Mono<Void>> tareas = grouped.entrySet().stream()
                            .map(entry -> {
                                String nombre = entry.getKey();
                                List<EventoCalendarioParseado> cuotas = new ArrayList<>(entry.getValue());
                                cuotas.sort(Comparator.comparingInt(EventoCalendarioParseado::numeroCuota));

                                // Generar contratoId temporal basado en nombre
                                String contratoId = "CAL-" + nombre.replaceAll("\\s+", "-").toUpperCase();

                                LocalDate hoy = LocalDate.now(NivelMoraCalculadora.LIMA);

                                // Construir cronograma con estados correctos:
                                // pagada → PAGADA, vencida → VENCIDA, futura → PENDIENTE
                                List<CuotaCronogramaDocument> cronograma = cuotas.stream()
                                        .map(c -> {
                                            String estado;
                                            if (c.pagada()) {
                                                estado = "PAGADA";
                                            } else if (c.fechaVencimiento() != null
                                                    && c.fechaVencimiento().isBefore(hoy)) {
                                                estado = "VENCIDA";
                                            } else {
                                                estado = "PENDIENTE";
                                            }
                                            return CuotaCronogramaDocument.builder()
                                                    .cuota(c.numeroCuota())
                                                    .cuotaNum(c.numeroCuota())
                                                    .monto(c.monto())
                                                    .fechaVencimiento(c.fechaVencimiento() != null
                                                            ? c.fechaVencimiento().toString() : null)
                                                    .estado(estado)
                                                    .build();
                                        })
                                        .collect(Collectors.toList());

                                // Saldo = suma solo de cuotas no pagadas
                                double saldoTotal = cuotas.stream()
                                        .filter(c -> !c.pagada())
                                        .mapToDouble(EventoCalendarioParseado::monto)
                                        .sum();

                                // capitalOriginal = suma de todas las cuotas (incluyendo pagadas)
                                double capitalOriginal = cuotas.stream()
                                        .mapToDouble(EventoCalendarioParseado::monto)
                                        .sum();

                                // Fecha de la PRIMERA cuota impaga (para calcular mora)
                                String fechaPrimeraCuotaImpaga = cuotas.stream()
                                        .filter(c -> !c.pagada() && c.fechaVencimiento() != null)
                                        .map(c -> c.fechaVencimiento().toString())
                                        .findFirst()
                                        .orElse(null);

                                // Días de mora = días desde la cuota vencida más antigua no pagada
                                int diasMora = cuotas.stream()
                                        .filter(c -> !c.pagada() && c.fechaVencimiento() != null
                                                && c.fechaVencimiento().isBefore(hoy))
                                        .mapToInt(c -> (int) ChronoUnit.DAYS.between(c.fechaVencimiento(), hoy))
                                        .max()
                                        .orElse(0);

                                String nivelEstrategia = NivelMoraCalculadora.calcularNivel(diasMora);
                                String estadoCaso = diasMora > 0 ? "INTERVENCION_REQUERIDA" : "EN_SEGUIMIENTO";

                                // Split nombre (apellidos primero en Perú): "VALDEZ MOTA RAFAEL DANIEL"
                                String[] partes = nombre.trim().split("\\s+");
                                String apellidos = partes.length >= 2
                                        ? partes[0] + " " + partes[1] : partes[0];
                                String nombres = partes.length >= 3
                                        ? String.join(" ", Arrays.copyOfRange(partes, 2, partes.length))
                                        : (partes.length == 2 ? partes[1] : "");

                                DatosTitularDocument titular = DatosTitularDocument.builder()
                                        .nombres(nombres)
                                        .apellidos(apellidos)
                                        .build();

                                IniciarCasoCommand command = new IniciarCasoCommand(
                                        contratoId, storeId, titular,
                                        null,   // fiador — no disponible desde calendario
                                        null,   // motoDescripcion — no disponible desde calendario
                                        capitalOriginal, saldoTotal,
                                        nivelEstrategia, estadoCaso,
                                        agenteAsignadoId, agenteNombre,
                                        fechaPrimeraCuotaImpaga, cronograma, usuarioId
                                );

                                return iniciarCasoUseCase.ejecutar(command)
                                        .doOnSuccess(__ -> creados.incrementAndGet())
                                        .doOnError(e -> {
                                            log.error("[Calendar] Error creando caso para {}: {}", nombre, e.getMessage());
                                            errores.incrementAndGet();
                                        })
                                        .onErrorResume(e -> Mono.empty())
                                        .then();
                            })
                            .toList();

                    return Mono.when(tareas)
                            .thenReturn(new ImportarCalendarioResultDto(
                                    grouped.size(), creados.get(), errores.get()
                            ));
                });
    }
}
