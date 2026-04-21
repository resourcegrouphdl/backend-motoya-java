package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.in.EnviarBienvenidaWhatsAppUseCase;
import com.motoyav2.evaluacion.domain.port.in.ReenviarBienvenidaWaUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReenviarBienvenidaWaUseCaseImpl implements ReenviarBienvenidaWaUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository   clienteRepository;
    private final EnviarBienvenidaWhatsAppUseCase enviarBienvenida;

    @Override
    public Mono<Void> reenviar(String solicitudId, boolean esFiador) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new NotFoundException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(sol -> resolverCliente(sol, esFiador))
                .flatMap(cliente -> {
                    if (cliente.getTelefono1() == null || cliente.getTelefono1().isBlank()) {
                        return Mono.error(new BadRequestException("El cliente no tiene teléfono registrado"));
                    }
                    log.info("[BIENVENIDA-REENVIO] Reenviando a {} esFiador={} telefono={}",
                            cliente.getNombreCompleto(), esFiador, cliente.getTelefono1());
                    return enviarBienvenida.enviar(solicitudId, cliente.getTelefono1(),
                            cliente.getNombreCompleto(), esFiador);
                });
    }

    private Mono<Cliente> resolverCliente(Solicitud sol, boolean esFiador) {
        String clienteId = esFiador ? sol.getFiadorId() : sol.getTitularId();
        if (clienteId == null || clienteId.isBlank()) {
            return Mono.error(new BadRequestException(
                    esFiador ? "La solicitud no tiene fiador registrado" : "La solicitud no tiene titular"));
        }
        return clienteRepository.findById(clienteId)
                .switchIfEmpty(Mono.error(new NotFoundException("Cliente no encontrado: " + clienteId)));
    }
}
