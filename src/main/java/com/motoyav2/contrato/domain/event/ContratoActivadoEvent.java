package com.motoyav2.contrato.domain.event;

import java.util.List;

/**
 * Publicado por CompletarContratoService cuando un contrato alcanza estado COMPLETADO.
 * El handler IniciarCasoEventHandler lo consume para crear automáticamente el caso
 * de cobranza, cerrando la brecha evaluación→cobranza.
 *
 * Controlado por: cobranzas.auto-iniciar-caso.enabled (false = dry_run, solo loga)
 */
public record ContratoActivadoEvent(
        String contratoId,
        String storeId,
        String titularNombres,
        String titularApellidos,
        String titularTipoDocumento,
        String titularNumeroDocumento,
        String titularTelefono,
        String titularEmail,
        String titularDireccion,
        String titularDistrito,
        String titularProvincia,
        String titularDepartamento,
        String fiadorNombres,
        String fiadorApellidos,
        String fiadorTipoDocumento,
        String fiadorNumeroDocumento,
        String fiadorTelefono,
        String fiadorEmail,
        String fiadorParentesco,
        String motoDescripcion,
        Double capitalOriginal,
        Double saldoActual,
        String fechaVencimientoPrimerCuota,
        List<CuotaActivadaDto> cronograma,
        String completadoPor
) {
    /** Datos mínimos de cada cuota necesarios para construir el cronograma de cobranza. */
    public record CuotaActivadaDto(
            int numeroCuota,
            String fechaVencimiento,
            double monto,
            String estado
    ) {}
}
