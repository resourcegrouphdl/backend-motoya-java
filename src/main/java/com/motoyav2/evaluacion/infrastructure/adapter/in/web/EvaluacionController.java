package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.CrearExpedienteUseCase;
import com.motoyav2.evaluacion.application.port.in.ListarEvaluacionesUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.CreditAplication;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.ListarEvaluacionesResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.mapper.EvaluacionResumenDtoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/evaluacion")
@RequiredArgsConstructor
public class EvaluacionController {

    private final CrearExpedienteUseCase crearExpedienteUseCase;
    private final ListarEvaluacionesUseCase listarEvaluacionesUseCase;

    @PostMapping("creacion")
    public Mono<String> crearExpediente(
            @RequestBody @NotNull CreditAplication creditAplication) {

        return crearExpedienteUseCase.ejecutar(
                creditAplication.codigoDeSolicitud(),
                creditAplication.formularioId());
    }

    @GetMapping
    public Mono<ListarEvaluacionesResponse> listarEvaluaciones(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int porPagina) {

        return listarEvaluacionesUseCase.ejecutar(pagina, porPagina)
                .map(resultado -> ListarEvaluacionesResponse.builder()
                        .success(true)
                        .data(resultado.getItems().stream()
                                .map(EvaluacionResumenDtoMapper::toDto)
                                .toList())
                        .total(resultado.getTotal())
                        .pagina(resultado.getPagina())
                        .porPagina(resultado.getPorPagina())
                        .build());
    }
}
