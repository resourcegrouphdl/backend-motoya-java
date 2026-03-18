package com.motoyav2.voucherextraction.domain.strategy;

import java.util.Map;

/**
 * Contrato para estrategias de extracción de campos por entidad bancaria.
 *
 * Principio OCP: nuevo banco = nueva clase que implementa esta interfaz.
 * Nada existente se modifica.
 *
 * Las implementaciones se registran como beans de Spring con @Order para
 * determinar la prioridad de detección. GenéricoStrategy siempre es el último.
 */
public interface BancoStrategy {

    /** Identificador del banco: BCP, INTERBANK, BBVA, SCOTIABANK, GENERICO… */
    String getBancoNombre();

    /**
     * Devuelve true si el texto del voucher corresponde a este banco.
     * Se evalúan en orden @Order; se usa la primera que devuelva true.
     */
    boolean soporta(String fullText);

    /**
     * Extrae campos usando patrones propios del banco.
     * Las claves retornadas son en camelCase español (montoPagado, fechaPago, etc.).
     */
    Map<String, String> extraer(String fullText);
}
