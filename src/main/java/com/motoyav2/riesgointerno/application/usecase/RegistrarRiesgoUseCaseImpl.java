package com.motoyav2.riesgointerno.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import com.motoyav2.riesgointerno.domain.port.in.RegistrarRiesgoUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrarRiesgoUseCaseImpl implements RegistrarRiesgoUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Mono<RegistroRiesgo> registrar(Command cmd) {
        Timestamp ahora = Timestamp.now();
        Timestamp fechaIncidente = parseFecha(cmd.fechaIncidente(), ahora);

        RegistroRiesgo registro = RegistroRiesgo.builder()
                .dniRegistrado(cmd.dniRegistrado())
                .nombreRegistrado(cmd.nombreRegistrado())
                .telefonos(cmd.telefonos() != null ? cmd.telefonos() : List.of())
                .tipoSujeto(cmd.tipoSujeto())
                .nivelRiesgo(cmd.nivelRiesgo())
                .estadoRegistro(cmd.estadoRegistro())
                .tipoRiesgo(cmd.tipoRiesgo())
                .contratoIdRelacionado(cmd.contratoIdRelacionado())
                .solicitudIdRelacionado(cmd.solicitudIdRelacionado())
                .montoDeudaPendiente(cmd.montoDeudaPendiente())
                .fechaIncidente(fechaIncidente)
                .descripcion(cmd.descripcion())
                .evidencias(cmd.evidencias() != null ? cmd.evidencias() : List.of())
                .condicionesRehabilitacion(cmd.condicionesRehabilitacion() != null ? cmd.condicionesRehabilitacion() : List.of())
                .registradoPor(cmd.registradoPor())
                .historialCambios(List.of())
                .fechaRegistro(ahora)
                .updatedAt(ahora)
                .build();

        return repository.create(registro);
    }

    private Timestamp parseFecha(String fechaStr, Timestamp fallback) {
        if (fechaStr == null || fechaStr.isBlank()) return fallback;
        try {
            LocalDate ld = LocalDate.parse(fechaStr);
            Instant instant = ld.atStartOfDay(ZoneId.of("America/Lima")).toInstant();
            return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), 0);
        } catch (Exception e) {
            return fallback;
        }
    }
}
