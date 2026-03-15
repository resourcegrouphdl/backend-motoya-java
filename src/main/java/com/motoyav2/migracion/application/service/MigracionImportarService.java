package com.motoyav2.migracion.application.service;

import com.motoyav2.migracion.application.dto.ImportarCalendarResponse;
import com.motoyav2.migracion.domain.document.CuotaStagingDocument;
import com.motoyav2.migracion.domain.document.MigracionStagingDocument;
import com.motoyav2.migracion.domain.repository.MigracionStagingRepository;
import com.motoyav2.migracion.infrastructure.adapter.out.calendar.EventoMigracionParseado;
import com.motoyav2.migracion.infrastructure.adapter.out.calendar.MigracionCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigracionImportarService {

    private final MigracionCalendarService calendarService;
    private final MigracionStagingRepository repository;

    public Mono<ImportarCalendarResponse> importar(String usuarioId) {
        return calendarService.obtenerEventos()
                .collectMultimap(EventoMigracionParseado::nombreCompleto)
                .flatMap(grouped -> {
                    int clientesDetectados = grouped.size();
                    AtomicInteger creados   = new AtomicInteger(0);
                    AtomicInteger duplicados = new AtomicInteger(0);

                    log.info("[Migracion-Importar] {} clientes detectados en Calendar", clientesDetectados);

                    List<Mono<Void>> tareas = grouped.entrySet().stream()
                            .map(entry -> procesarCliente(
                                    entry.getKey(),
                                    new ArrayList<>(entry.getValue()),
                                    creados, duplicados, usuarioId))
                            .toList();

                    return Mono.when(tareas)
                            .thenReturn(new ImportarCalendarResponse(
                                    "OK",
                                    clientesDetectados,
                                    creados.get(),
                                    duplicados.get(),
                                    creados.get() + " clientes importados desde Google Calendar. "
                                            + duplicados.get() + " ya existían en staging."
                            ));
                })
                .onErrorResume(e -> {
                    log.error("[Migracion-Importar] Error: {}", e.getMessage());
                    return Mono.just(new ImportarCalendarResponse(
                            "ERROR", 0, 0, 0,
                            "Error al conectar con Google Calendar: " + e.getMessage()
                    ));
                });
    }

    private Mono<Void> procesarCliente(String nombre,
                                        List<EventoMigracionParseado> cuotas,
                                        AtomicInteger creados,
                                        AtomicInteger duplicados,
                                        String usuarioId) {
        if (cuotas.isEmpty()) return Mono.empty();

        cuotas.sort(Comparator.comparingInt(EventoMigracionParseado::numeroCuota));

        String fechaInicio = cuotas.get(0).fechaVencimiento() != null
                ? cuotas.get(0).fechaVencimiento().toString() : "";

        return repository.findByClienteNombreCalendarAndFechaInicio(nombre, fechaInicio)
                .hasElements()
                .flatMap(existe -> {
                    if (existe) {
                        log.debug("[Migracion-Importar] Duplicado ignorado: {} / {}", nombre, fechaInicio);
                        duplicados.incrementAndGet();
                        return Mono.empty();
                    }
                    MigracionStagingDocument doc = buildStagingDocument(nombre, cuotas, fechaInicio, usuarioId);
                    return repository.save(doc)
                            .doOnSuccess(d -> {
                                log.debug("[Migracion-Importar] Staging creado id={} cliente={}", d.getId(), nombre);
                                creados.incrementAndGet();
                            })
                            .then();
                });
    }

    private MigracionStagingDocument buildStagingDocument(String nombre,
                                                           List<EventoMigracionParseado> cuotas,
                                                           String fechaInicio,
                                                           String usuarioId) {
        double montoCuota     = cuotas.get(0).monto();
        int    totalCuotas    = cuotas.size();
        double capitalInferido = montoCuota * totalCuotas;

        List<Integer> cuotasPagadas = cuotas.stream()
                .filter(EventoMigracionParseado::pagada)
                .map(EventoMigracionParseado::numeroCuota)
                .toList();

        List<CuotaStagingDocument> cronograma = cuotas.stream()
                .map(c -> CuotaStagingDocument.builder()
                        .cuota(c.numeroCuota())
                        .fechaVencimiento(c.fechaVencimiento() != null
                                ? c.fechaVencimiento().toString() : null)
                        .pagada(c.pagada())
                        .tituloOriginal(c.tituloOriginal())
                        .build())
                .toList();

        return MigracionStagingDocument.builder()
                .estado("INCOMPLETO")
                .completitud(0)
                .clienteNombreCalendar(nombre)
                .totalCuotas(totalCuotas)
                .montoCuota(montoCuota)
                .capitalInferido(capitalInferido)
                .fechaInicio(fechaInicio)
                .cuotasPagadas(cuotasPagadas)
                .cronogramaCalendar(cronograma)
                .creadoEn(new Date())
                .creadoPor(usuarioId)
                .actualizadoEn(new Date())
                .actualizadoPor(usuarioId)
                .build();
    }
}
