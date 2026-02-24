package com.motoyav2.cobranza.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Puerto de salida para el correlativo de comprobantes.
 * La implementación DEBE usar una Firestore transaction para garantizar unicidad.
 */
public interface NumeradorPort {

    /**
     * Incrementa atómicamente el correlativo de la serie y retorna el número formateado.
     * Ejemplo: siguienteNumero("B001") → "B001-00000023"
     */
    Mono<String> siguienteNumero(String serie);
}
