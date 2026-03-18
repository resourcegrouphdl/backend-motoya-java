package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.port.in.GenerarCertificadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.port.out.StoragePort;
import com.motoyav2.evaluacion.infrastructure.pdf.CertificadoPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GenerarCertificadoUseCaseImpl implements GenerarCertificadoUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;
    private final CertificadoPdfService certificadoPdfService;
    private final StoragePort storagePort;

    @Override
    public Mono<String> ejecutar(String numeroSolicitud) {
        return solicitudRepository.findByNumeroSolicitud(numeroSolicitud)
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(numeroSolicitud)))
                .flatMap(solicitud -> obtenerExpedienteUseCase.ejecutar(solicitud.getId())
                        .flatMap(expediente -> {
                            byte[] pdf = certificadoPdfService.generar(expediente);
                            String path = "contratos/certificados/" + solicitud.getId();
                            String fileName = "certificado_" + numeroSolicitud + ".pdf";
                            return storagePort.uploadPdf(pdf, path, fileName)
                                    .flatMap(url -> {
                                        Timestamp ahora = Timestamp.now();
                                        Map<String, Object> updates = new HashMap<>();
                                        updates.put("certificadoGenerado", true);
                                        updates.put("urlCertificado", url);
                                        updates.put("fechaGeneracionCertificado", ahora);
                                        updates.put("updatedAt", ahora);
                                        return solicitudRepository.updateFields(solicitud.getId(), updates)
                                                .thenReturn(url);
                                    });
                        }));
    }
}
