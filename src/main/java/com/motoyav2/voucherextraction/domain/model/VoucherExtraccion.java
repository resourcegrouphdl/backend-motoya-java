package com.motoyav2.voucherextraction.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Resultado final estructurado de la extracción de un voucher bancario.
 * Combina lo detectado por Form Parser, las estrategias regex por banco
 * y el enriquecimiento LLM cuando aplica.
 */
public record VoucherExtraccion(
        /** COMPLETADO | COMPLETADO_SIN_CAMPOS | ERROR | OMITIDO */
        String status,
        /** Banco detectado: BCP, INTERBANK, BBVA, SCOTIABANK, GENERICO */
        String banco,
        /** Campos extraídos en camelCase español: montoPagado, fechaPago, pagadoA, etc. */
        Map<String, String> campos,
        /** true si se invocó Claude Haiku para enriquecer campos faltantes */
        boolean enriquecidoConLlm,
        String procesadoEn,
        String error
) {

    public static VoucherExtraccion omitido() {
        return new VoucherExtraccion("OMITIDO", null, Map.of(), false, Instant.now().toString(), null);
    }

    public static VoucherExtraccion error(String msg) {
        return new VoucherExtraccion("ERROR", null, Map.of(), false, Instant.now().toString(), msg);
    }
}
