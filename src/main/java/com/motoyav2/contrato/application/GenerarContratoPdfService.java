package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.DocumentoGenerado;
import com.motoyav2.contrato.domain.port.in.GenerarContratoPdfUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerarContratoPdfService implements GenerarContratoPdfUseCase {

    private final ContratoRepository contratoRepository;
    private final PdfGenerationService pdfGenerationService;

    @Override
    public Flux<DocumentoGenerado> documentosGenerados(String id) {
        return contratoRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + id)))
                .flatMap(contrato -> pdfGenerationService.generarTodos(contrato, contrato.cuotas())
                        .flatMap(docs -> guardarYTransicionar(contrato, docs)))
                .flatMapMany(Flux::fromIterable);
    }

    private Mono<List<DocumentoGenerado>> guardarYTransicionar(Contrato contrato, List<DocumentoGenerado> docs) {
        ContratoStateMachine.validateTransition(contrato.estado(), EstadoContrato.FIRMA_PENDIENTE);

        Contrato actualizado = new Contrato(
                contrato.id(), contrato.numeroContrato(),
                EstadoContrato.FIRMA_PENDIENTE,
                FaseContrato.FIRMA,
                contrato.titular(), contrato.fiador(), contrato.tienda(),
                contrato.datosFinancieros(), contrato.boucheresPagoInicial(),
                contrato.facturaVehiculo(), contrato.cuotas(), docs,
                contrato.evidenciasFirma(), contrato.notificaciones(),
                contrato.creadoPor(), contrato.evaluacionId(),
                contrato.motivoRechazo(), contrato.fechaCreacion(),
                Instant.now(), contrato.contratoParaImprimir(),
                contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actaDeEntrega()
        );
        return contratoRepository.save(actualizado).thenReturn(docs);
    }
}
