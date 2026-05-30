package com.motoyav2.riesgointerno.infrastructure.adapter.in.web;

import com.motoyav2.riesgointerno.domain.enums.EstadoRegistro;
import com.motoyav2.riesgointerno.domain.enums.NivelRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoSujeto;
import com.motoyav2.riesgointerno.domain.port.in.*;
import com.motoyav2.riesgointerno.infrastructure.adapter.in.web.request.CambiarEstadoRiesgoRequest;
import com.motoyav2.riesgointerno.infrastructure.adapter.in.web.request.CambiarNivelRiesgoRequest;
import com.motoyav2.riesgointerno.infrastructure.adapter.in.web.request.CrearRegistroRiesgoRequest;
import com.motoyav2.riesgointerno.infrastructure.adapter.in.web.response.PagedRegistrosDto;
import com.motoyav2.riesgointerno.infrastructure.adapter.in.web.response.RegistroRiesgoDto;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/riesgo-interno")
@RequiredArgsConstructor
public class RiesgoInternoController {

    private final RegistrarRiesgoUseCase registrarRiesgoUseCase;
    private final ListarRegistrosUseCase listarRegistrosUseCase;
    private final ObtenerRegistroUseCase obtenerRegistroUseCase;
    private final CambiarEstadoRiesgoUseCase cambiarEstadoUseCase;
    private final CambiarNivelRiesgoUseCase cambiarNivelUseCase;
    private final BuscarPorTelefonoUseCase buscarPorTelefonoUseCase;
    private final BuscarPorDniUseCase buscarPorDniUseCase;
    private final EliminarRegistroUseCase eliminarRegistroUseCase;

    // ── POST /api/v1/riesgo-interno ─────────────────────────────────────────
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegistroRiesgoDto> crear(
            @Valid @RequestBody CrearRegistroRiesgoRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        var command = new RegistrarRiesgoUseCase.Command(
                req.getDniRegistrado(),
                req.getNombreRegistrado(),
                req.getTelefonos(),
                parseEnum(TipoSujeto.class, req.getTipoSujeto(), "tipoSujeto"),
                parseEnum(NivelRiesgo.class, req.getNivelRiesgo(), "nivelRiesgo"),
                parseEnum(EstadoRegistro.class, req.getEstadoRegistro(), "estadoRegistro"),
                TipoRiesgo.fromString(req.getTipoRiesgo()),
                req.getContratoIdRelacionado(),
                req.getSolicitudIdRelacionado(),
                req.getMontoDeudaPendiente(),
                req.getFechaIncidente(),
                req.getDescripcion(),
                req.getEvidencias(),
                req.getCondicionesRehabilitacion(),
                principal != null ? principal.uid() : "sistema"
        );

        return registrarRiesgoUseCase.registrar(command)
                .map(RegistroRiesgoDto::from);
    }

    // ── GET /api/v1/riesgo-interno ──────────────────────────────────────────
    @GetMapping
    public Mono<PagedRegistrosDto> listar(
            @RequestParam(required = false) String nivelRiesgo,
            @RequestParam(required = false) String estadoRegistro,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return listarRegistrosUseCase.listar(nivelRiesgo, estadoRegistro, search, page, size)
                .map(result -> PagedRegistrosDto.from(
                        result.items().stream().map(RegistroRiesgoDto::from).toList(),
                        result.total(), result.page(), result.size()
                ));
    }

    // ── GET /api/v1/riesgo-interno/buscar ───────────────────────────────────
    @GetMapping("/buscar")
    public Mono<List<RegistroRiesgoDto>> buscar(
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String dni) {

        if (telefono != null && !telefono.isBlank()) {
            return buscarPorTelefonoUseCase.buscar(telefono)
                    .map(RegistroRiesgoDto::from)
                    .collectList();
        }
        if (dni != null && !dni.isBlank()) {
            return buscarPorDniUseCase.buscar(dni)
                    .map(RegistroRiesgoDto::from)
                    .collectList();
        }
        return Mono.error(new BadRequestException("Se requiere parámetro 'telefono' o 'dni'"));
    }

    // ── GET /api/v1/riesgo-interno/{id} ─────────────────────────────────────
    @GetMapping("/{id}")
    public Mono<RegistroRiesgoDto> obtener(@PathVariable String id) {
        return obtenerRegistroUseCase.obtener(id)
                .map(RegistroRiesgoDto::from);
    }

    // ── PUT /api/v1/riesgo-interno/{id}/estado ──────────────────────────────
    @PutMapping("/{id}/estado")
    public Mono<Void> cambiarEstado(
            @PathVariable String id,
            @Valid @RequestBody CambiarEstadoRiesgoRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        EstadoRegistro nuevoEstado = parseEnum(EstadoRegistro.class, req.getNuevoEstado(), "nuevoEstado");
        String uid = principal != null ? principal.uid() : "sistema";
        return cambiarEstadoUseCase.cambiarEstado(id, nuevoEstado, req.getMotivo(), uid);
    }

    // ── PUT /api/v1/riesgo-interno/{id}/nivel ───────────────────────────────
    @PutMapping("/{id}/nivel")
    public Mono<Void> cambiarNivel(
            @PathVariable String id,
            @Valid @RequestBody CambiarNivelRiesgoRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {

        NivelRiesgo nuevoNivel = parseEnum(NivelRiesgo.class, req.getNuevoNivel(), "nuevoNivel");
        String uid = principal != null ? principal.uid() : "sistema";
        return cambiarNivelUseCase.cambiarNivel(id, nuevoNivel, req.getMotivo(), uid);
    }

    // ── DELETE /api/v1/riesgo-interno/{id} ──────────────────────────────────
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> eliminar(@PathVariable String id) {
        return eliminarRegistroUseCase.eliminar(id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("El campo '" + field + "' es requerido");
        }
        try {
            return Enum.valueOf(clazz, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Valor inválido para '" + field + "': " + value);
        }
    }
}
