package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.dto.NombreResuelto;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.model.VerificacionIdentidadSnapshot;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Resuelve el nombre canónico de un cliente aplicando la política de fuente de verdad:
 *
 * <pre>
 *   Si verificacionIdentidad.exitoso == true
 *       → usar apiNombres + apiApellidoPaterno + apiApellidoMaterno  (RENIEC / Factiliza)
 *   Sino
 *       → usar los datos ingresados en el formulario
 * </pre>
 *
 * <p>Este resolver es utilizado por el módulo de contratos al momento de
 * crear la semilla del contrato, garantizando que el nombre del titular
 * y del fiador corresponda exactamente a lo registrado en el padrón oficial.</p>
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class NombreVerificadoResolver {

    private final ClienteRepository clienteRepository;

    /**
     * Retorna el {@link NombreResuelto} para el cliente con el {@code clienteId} dado.
     * Si el cliente no existe o no tiene verificación exitosa, usa los datos del formulario
     * y no lanza error (aplica graceful fallback).
     */
    public Mono<NombreResuelto> resolver(String clienteId) {
        return clienteRepository.findById(clienteId)
                .map(this::resolverDesdeCliente)
                .onErrorResume(ex -> {
                    log.warn("No se pudo obtener cliente {} para resolución de nombre. Causa: {}",
                            clienteId, ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Versión síncrona para cuando ya se tiene el {@link Cliente} en memoria.
     */
    public NombreResuelto resolverDesdeCliente(Cliente cliente) {
        VerificacionIdentidadSnapshot v = cliente.getVerificacionIdentidad();

        if (v != null && v.exitoso()
                && hasText(v.apiNombres())
                && hasText(v.apiApellidoPaterno())) {

            String apellidos = buildApellidos(v.apiApellidoPaterno(), v.apiApellidoMaterno());
            log.debug("Nombre resuelto desde RENIEC para cliente {}: {} {}",
                    cliente.getId(), v.apiNombres(), apellidos);
            return new NombreResuelto(v.apiNombres().trim().toUpperCase(),
                    apellidos, true);
        }

        // Fallback: datos del formulario
        String apellidosFormulario = buildApellidos(
                cliente.getApellidoPaterno(), cliente.getApellidoMaterno());
        String nombresFormulario = cliente.getNombres() != null
                ? cliente.getNombres().trim().toUpperCase() : "";

        log.debug("Nombre resuelto desde formulario para cliente {} (verificación no disponible)",
                cliente.getId());
        return new NombreResuelto(nombresFormulario, apellidosFormulario, false);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static String buildApellidos(String paterno, String materno) {
        String p = paterno  != null ? paterno.trim().toUpperCase()  : "";
        String m = materno  != null ? materno.trim().toUpperCase()  : "";
        return m.isEmpty() ? p : (p + " " + m).trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
