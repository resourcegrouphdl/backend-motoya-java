package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.GenerarContratoCommand;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.port.in.GenerarContratoUseCase;
import com.motoyav2.evaluacion.domain.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.port.out.StoragePort;
import com.motoyav2.evaluacion.infrastructure.pdf.ContratoPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GenerarContratoUseCaseImpl implements GenerarContratoUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;
    private final ContratoPdfService contratoPdfService;
    private final StoragePort storagePort;

    @Override
    public Mono<String> ejecutar(GenerarContratoCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> obtenerExpedienteUseCase.ejecutar(command.solicitudId())
                        .flatMap(expediente -> {
                            byte[] pdf = contratoPdfService.generar(expediente, command.camposAdicionales());
                            String path = "contratos/contratos-pdf/" + command.solicitudId();
                            String fileName = "contrato_" + solicitud.getNumeroSolicitud() + ".pdf";
                            return storagePort.uploadPdf(pdf, path, fileName)
                                    .flatMap(url -> {
                                        Timestamp ahora = Timestamp.now();
                                        Map<String, Object> updates = new HashMap<>();
                                        updates.put("contratoGenerado", true);
                                        updates.put("urlContrato", url);
                                        updates.put("fechaGeneracionContrato", ahora);
                                        updates.put("updatedAt", ahora);
                                        return solicitudRepository.updateFields(command.solicitudId(), updates)
                                                .thenReturn(url);
                                    });
                        }));
    }
}
