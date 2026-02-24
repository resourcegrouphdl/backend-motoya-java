package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.dto.EventoCalendarioParseado;
import com.motoyav2.cobranza.application.dto.ImportarCalendarioResultDto;
import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.calendar.GoogleCalendarService;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
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

                                // Construir cronograma desde los eventos del calendario
                                List<CuotaCronogramaDocument> cronograma = cuotas.stream()
                                        .map(c -> CuotaCronogramaDocument.builder()
                                                .cuotaNum(c.numeroCuota())
                                                .monto(c.monto())
                                                .fechaVencimiento(c.fechaVencimiento() != null
                                                        ? c.fechaVencimiento().toString() : null)
                                                .estado("VENCIDA")
                                                .build())
                                        .collect(Collectors.toList());

                                double saldoTotal = cuotas.stream()
                                        .mapToDouble(EventoCalendarioParseado::monto).sum();

                                // Fecha de la primera cuota como referencia de mora
                                String fechaPrimeraCuota = cuotas.get(0).fechaVencimiento() != null
                                        ? cuotas.get(0).fechaVencimiento().toString() : null;

                                // Split nombre en partes (apellidos primero en Perú)
                                String[] partes = nombre.trim().split("\\s+", 3);
                                String apellidos = partes.length >= 2
                                        ? partes[0] + (partes.length > 2 ? " " + partes[1] : "") : nombre;
                                String nombres = partes.length >= 3 ? partes[2]
                                        : (partes.length == 2 ? partes[1] : "");

                                DatosTitularDocument titular = DatosTitularDocument.builder()
                                        .nombres(nombres)
                                        .apellidos(apellidos)
                                        .build();

                                IniciarCasoCommand command = new IniciarCasoCommand(
                                        contratoId, storeId, titular, null,
                                        saldoTotal, saldoTotal,
                                        "MORA_TEMPRANA", "EN_SEGUIMIENTO",
                                        agenteAsignadoId, agenteNombre,
                                        fechaPrimeraCuota, cronograma, usuarioId
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
