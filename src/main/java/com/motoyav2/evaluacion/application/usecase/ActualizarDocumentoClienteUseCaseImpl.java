package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.ActualizarDocumentoClienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActualizarDocumentoClienteUseCaseImpl implements ActualizarDocumentoClienteUseCase {

    private static final Set<String> TIPOS_VALIDOS = Set.of("DNI", "CE");

    private final ClienteRepository clienteRepository;

    @Override
    public Mono<Void> actualizarDocumento(String clienteId, String documentType, String documentNumber) {
        if (!TIPOS_VALIDOS.contains(documentType)) {
            return Mono.error(new BadRequestException("Tipo de documento inválido. Use DNI o CE"));
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            return Mono.error(new BadRequestException("El número de documento no puede estar vacío"));
        }
        Map<String, Object> fields = Map.of(
                "documentType",   documentType,
                "documentNumber", documentNumber.trim(),
                "updatedAt",      Timestamp.now()
        );
        return clienteRepository.updateFields(clienteId, fields);
    }
}
