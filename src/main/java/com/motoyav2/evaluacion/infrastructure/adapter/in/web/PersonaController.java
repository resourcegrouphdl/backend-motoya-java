package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.domain.port.in.BuscarPersonaUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.response.PersonaResumenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Endpoints para la central de riesgo interna y autocomplete de personas.
 * Consumido por el formulario del vendedor al ingresar DNI de titular o fiador.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/personas")
@RequiredArgsConstructor
public class PersonaController {

    private final BuscarPersonaUseCase buscarPersonaUseCase;

    /**
     * Busca una persona por número de documento.
     * Retorna sus datos de autocomplete + alertas de la central de riesgo interna.
     *
     * <p>El frontend usa esta respuesta para:
     * <ul>
     *   <li>Pre-llenar el formulario con datos conocidos</li>
     *   <li>Mostrar alertas BLOQUEANTES (impiden avanzar) o ADVERTENCIAS (requieren confirmación)</li>
     * </ul>
     *
     * <p>Si la persona no tiene historial previo se retorna 204 (sin datos — no es un error).
     *
     * @param documentNumber DNI u otro número de documento (8+ caracteres)
     */
    @GetMapping("/buscar")
    public Mono<PersonaResumenResponse> buscarPorDocumento(
            @RequestParam String documentNumber) {

        if (documentNumber == null || documentNumber.isBlank() || documentNumber.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El parámetro documentNumber es requerido y debe tener al menos 6 caracteres");
        }

        log.info("[PERSONAS] Búsqueda por documentNumber={}", documentNumber);

        return buscarPersonaUseCase.ejecutar(documentNumber.trim())
                .map(PersonaResumenResponse::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NO_CONTENT, "No se encontró historial para el documento " + documentNumber)));
    }
}
