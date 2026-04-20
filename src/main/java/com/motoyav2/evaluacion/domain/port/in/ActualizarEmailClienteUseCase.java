package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.domain.model.ValidacionEmail;
import reactor.core.publisher.Mono;

public interface ActualizarEmailClienteUseCase {
    /**
     * Actualiza el email del cliente, re-ejecuta la validación MX
     * y persiste ambos valores en Firestore.
     *
     * @return resultado de la validación del nuevo email
     */
    Mono<ValidacionEmail> actualizarEmail(String clienteId, String nuevoEmail);
}
