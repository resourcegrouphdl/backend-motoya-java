package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.EvidenciaFirma;
import com.motoyav2.contrato.domain.port.in.ValidarEvidenciaFirmaUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidarEvidenciaFirmaService implements ValidarEvidenciaFirmaUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Contrato> validar(String contratoId, String evidenciaId,
                                  EstadoValidacion estado, String observacion, String validadoPor) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.evidenciasFirma() == null || contrato.evidenciasFirma().isEmpty()) {
                        return Mono.error(new BadRequestException("El contrato no tiene evidencias de firma"));
                    }

                    boolean encontrada = contrato.evidenciasFirma().stream()
                            .anyMatch(e -> evidenciaId.equals(e.id()));
                    if (!encontrada) {
                        return Mono.error(new NotFoundException(
                                "Evidencia de firma no encontrada: " + evidenciaId));
                    }

                    Instant now = Instant.now();
                    List<EvidenciaFirma> evidenciasActualizadas = contrato.evidenciasFirma().stream()
                            .map(e -> {
                                if (!evidenciaId.equals(e.id())) return e;
                                return EvidenciaFirma.builder()
                                        .id(e.id())
                                        .tipoEvidencia(e.tipoEvidencia())
                                        .urlEvidencia(e.urlEvidencia())
                                        .nombreArchivo(e.nombreArchivo())
                                        .tipoArchivo(e.tipoArchivo())
                                        .tamanioBytes(e.tamanioBytes())
                                        .fechaSubida(e.fechaSubida())
                                        .subidoPor(e.subidoPor())
                                        .descripcion(e.descripcion())
                                        .estadoValidacion(estado)
                                        .observacionesValidacion(observacion)
                                        .validadoPor(validadoPor)
                                        .fechaValidacion(now)
                                        .build();
                            })
                            .toList();

                    boolean todasAprobadas = evidenciasActualizadas.stream()
                            .allMatch(e -> EstadoValidacion.APROBADO == e.estadoValidacion());

                    EstadoContrato nuevoEstado = todasAprobadas
                            && contrato.estado() == EstadoContrato.FIRMA_PENDIENTE
                            && ContratoStateMachine.canTransition(contrato.estado(), EstadoContrato.FIRMADO)
                            ? EstadoContrato.FIRMADO
                            : contrato.estado();

                    FaseContrato nuevaFase = nuevoEstado == EstadoContrato.FIRMADO
                            ? FaseContrato.VIGENTE
                            : contrato.fase();

                    Contrato actualizado = new Contrato(
                            contrato.id(), contrato.numeroContrato(), nuevoEstado, nuevaFase,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), evidenciasActualizadas,
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actaDeEntrega()
                    );

                    return contratoRepository.save(actualizado);
                });
    }
}
