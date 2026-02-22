package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.assembler.ExpedienteAssembler;
import com.motoyav2.evaluacion.application.port.in.CrearExpedienteUseCase;
import com.motoyav2.evaluacion.application.port.out.ExpedientePort;
import com.motoyav2.evaluacion.application.port.out.SolicitudPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrearExpedienteService implements CrearExpedienteUseCase {

    private final SolicitudPort solicitudPort;
    private final ExpedienteAssembler expedienteAssembler;
    private final ExpedientePort expedientePort;

    @Override
    public Mono<String> ejecutar(String codigoDeSolicitud, String formularioId) {
        log.info("Iniciando creación de expediente para solicitud: {}", codigoDeSolicitud);

        return solicitudPort.obtenerSolicitud(formularioId)
                .flatMap(expedienteAssembler::ensamblar)
                .flatMap(expedientePort::guardar);
    }
}
