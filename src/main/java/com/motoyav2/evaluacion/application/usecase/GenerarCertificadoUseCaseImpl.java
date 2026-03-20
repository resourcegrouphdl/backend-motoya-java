package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.port.in.GenerarCertificadoUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * El certificado de aprobación es generado por un servicio externo dedicado.
 * Este use case simplemente recupera la URL del certificado que el servicio
 * externo ya almacenó en Firestore (campo urlCertificado).
 */
@Service
@RequiredArgsConstructor
public class GenerarCertificadoUseCaseImpl implements GenerarCertificadoUseCase {

    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<String> ejecutar(String solicitudIdOrNumero) {
        return solicitudRepository.findById(solicitudIdOrNumero)
                .switchIfEmpty(solicitudRepository.findByNumeroSolicitud(solicitudIdOrNumero))
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(solicitudIdOrNumero)))
                .flatMap(solicitud -> {
                    if (solicitud.getUrlCertificado() != null && !solicitud.getUrlCertificado().isBlank()) {
                        return Mono.just(solicitud.getUrlCertificado());
                    }
                    return Mono.error(new RecursoNoEncontradoException(
                            "El certificado aún no está disponible para la solicitud: " + solicitudIdOrNumero));
                });
    }
}
