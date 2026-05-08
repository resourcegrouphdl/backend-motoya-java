package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.dto.CasoResumenDto;
import com.motoyav2.cobranza.application.dto.VehiculoDto;
import com.motoyav2.cobranza.application.dto.Vista360CasoDto;
import com.motoyav2.cobranza.application.dto.VoucherVista360Dto;
import com.motoyav2.cobranza.application.port.in.AsignarAgenteUseCase;
import com.motoyav2.cobranza.application.port.in.ListarCasosUseCase;
import com.motoyav2.cobranza.application.port.in.ObtenerCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.AsignarAgenteCommand;
import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.port.out.*;
import com.motoyav2.cobranza.domain.NivelMoraCalculadora;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.contrato.domain.model.FacturaVehiculo;
import com.motoyav2.contrato.domain.port.in.ObtenerContratoUseCase;
import com.motoyav2.shared.exception.NotFoundException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CasoCobranzaService implements ListarCasosUseCase, ObtenerCasoUseCase, AsignarAgenteUseCase {

    private final CasoCobranzaPort casoPort;
    private final EventoCobranzaPort eventoPort;
    private final MovimientoPort movimientoPort;
    private final PromesaPort promesaPort;
    private final AcuerdoPort acuerdoPort;
    private final VoucherPort voucherPort;
    private final ObtenerContratoUseCase obtenerContratoUseCase;

    // -------------------------------------------------------------------------
    // ListarCasosUseCase
    // -------------------------------------------------------------------------

    @Override
    public Flux<CasoResumenDto> ejecutar(ListarCasosQuery query) {
        return casoPort.query(query)
                .map(this::toResumen)
                // Filtro en memoria por prioridad (no almacenada en Firestore)
                .filter(r -> query.prioridad() == null || query.prioridad().equalsIgnoreCase(r.prioridad()))
                // Orden: nivelEstrategia desc (peso negocio) → diasMora desc
                .sort(Comparator
                        .comparingInt((CasoResumenDto r) -> nivelOrd(r.nivelEstrategia()))
                        .reversed()
                        .thenComparingInt(CasoResumenDto::diasMora).reversed())
                // Paginación offset
                .skip((long) query.page() * query.size())
                .take(query.size());
    }

    // -------------------------------------------------------------------------
    // ObtenerCasoUseCase — Vista 360
    // -------------------------------------------------------------------------

    @Override
    public Mono<Vista360CasoDto> ejecutar(String contratoId) {
        Mono<CasoCobranzaDocument> casoMono = casoPort.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Caso no encontrado: " + contratoId)));

        // Datos del vehículo opcionales: si el contrato no existe o no tiene factura → null
        Mono<VehiculoDto> vehiculoMono = obtenerContratoUseCase.obtenerPorId(contratoId)
                .mapNotNull(c -> c.facturaVehiculo() != null ? toVehiculoDto(c.facturaVehiculo()) : null)
                .onErrorResume(e -> {
                    log.debug("[Vista360] Sin datos de vehículo contratoId={}: {}", contratoId, e.getMessage());
                    return Mono.empty();
                });

        return Mono.zip(
                casoMono,
                eventoPort.findByContratoId(contratoId).collectList(),
                movimientoPort.findByContratoId(contratoId).collectList(),
                promesaPort.findByContratoId(contratoId).collectList(),
                acuerdoPort.findByContratoId(contratoId).collectList(),
                voucherPort.findByContratoId(contratoId)
                           .map(v -> new VoucherVista360Dto(v.getId(), v.getEstado(), v.getFuente(),
                                                           v.getThumbPath(), v.getImagenPath(),
                                                           v.getMontoDetectado(), v.getComprobanteId(),
                                                           v.getCreadoEn()))
                           .collectList()
        ).flatMap(t -> vehiculoMono
                .map(veh -> new Vista360CasoDto(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), veh, t.getT6()))
                .defaultIfEmpty(new Vista360CasoDto(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), null, t.getT6()))
        );
    }

    private VehiculoDto toVehiculoDto(FacturaVehiculo f) {
        return new VehiculoDto(
                f.marcaVehiculo(),
                f.modeloVehiculo(),
                f.anioVehiculo(),
                f.colorVehiculo(),
                f.serieMotor(),
                f.serieChasis(),
                f.numeroFactura(),
                f.fechaEmision() != null ? f.fechaEmision().toString() : null
        );
    }

    // -------------------------------------------------------------------------
    // AsignarAgenteUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> ejecutar(AsignarAgenteCommand command) {
        return casoPort.findById(command.contratoId())
                .switchIfEmpty(Mono.error(new NotFoundException("Caso no encontrado: " + command.contratoId())))
                .flatMap(caso -> {
                    String agenteAnteriorId = caso.getAgenteAsignadoId();

                    caso.setAgenteAsignadoId(command.agenteNuevoId());
                    caso.setAgenteAsignadoNombre(command.agenteNuevoNombre());
                    caso.setActualizadoEn(new Date());
                    caso.setActualizadoPor(command.supervisorId());

                    EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                            .contratoId(command.contratoId())
                            .tipo("CASO_ASIGNADO")
                            .payload(Map.of(
                                    "agenteAnteriorId", agenteAnteriorId != null ? agenteAnteriorId : "",
                                    "agenteNuevoId", command.agenteNuevoId(),
                                    "agenteNuevoNombre", command.agenteNuevoNombre(),
                                    "motivo", command.motivo() != null ? command.motivo() : ""
                            ))
                            .usuarioId(command.supervisorId())
                            .usuarioNombre(command.supervisorNombre())
                            .automatico(false)
                            .creadoEn(new Date())
                            .build();

                    return casoPort.save(caso)
                            .then(eventoPort.append(command.contratoId(), evento))
                            .then();
                });
    }

    // -------------------------------------------------------------------------
    // Actualizar estadoCaso manualmente (admin)
    // -------------------------------------------------------------------------

    public Mono<Void> actualizarEstadoCaso(String contratoId, String nuevoEstado, String userId) {
        return casoPort.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Caso no encontrado: " + contratoId)))
                .flatMap(caso -> {
                    String estadoAnterior = caso.getEstadoCaso();
                    caso.setEstadoCaso(nuevoEstado);
                    caso.setActualizadoEn(new Date());
                    caso.setActualizadoPor(userId);

                    EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                            .contratoId(contratoId)
                            .tipo("ESTADO_CASO_ACTUALIZADO")
                            .payload(Map.of(
                                    "estadoAnterior", estadoAnterior != null ? estadoAnterior : "",
                                    "estadoNuevo",    nuevoEstado
                            ))
                            .usuarioId(userId)
                            .usuarioNombre(userId)
                            .automatico(false)
                            .creadoEn(new Date())
                            .build();

                    return casoPort.save(caso)
                            .then(eventoPort.append(contratoId, evento))
                            .then();
                });
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private CasoResumenDto toResumen(CasoCobranzaDocument d) {
        int diasMora = NivelMoraCalculadora.diasMora(d, LocalDate.now(NivelMoraCalculadora.LIMA));
        String prioridad = calcularPrioridad(d.getNivelEstrategia(), diasMora);
        String ultimaGestion = d.getUltimaGestion() != null
                ? d.getUltimaGestion().toInstant().toString()
                : null;

        return new CasoResumenDto(
                d.getContratoId(),
                d.getClienteNombre(),
                diasMora,
                d.getCapitalOriginal(),
                d.getSaldoActual(),
                d.getNivelEstrategia(),
                prioridad,
                d.getEstadoCaso(),
                d.getUltimaGestionResumen(),
                ultimaGestion,
                d.getProximaAccion(),
                d.getAgenteAsignadoNombre(),
                d.getClienteTelefono(),
                d.getMensajesNoLeidos(),
                d.getUltimaRespuestaCliente()
        );
    }

    /**
     * ALTA  → JUDICIAL | MORA_CRITICA | diasMora >= 60
     * MEDIA → MORA_MEDIA | diasMora >= 30
     * BAJA  → resto
     */
    private String calcularPrioridad(String nivel, int diasMora) {
        if ("JUDICIAL".equals(nivel) || "MORA_CRITICA".equals(nivel) || diasMora >= 60) return "ALTA";
        if ("MORA_MEDIA".equals(nivel) || diasMora >= 30) return "MEDIA";
        return "BAJA";
    }

    /** Peso para ordenar nivelEstrategia de mayor a menor urgencia. */
    private int nivelOrd(String nivel) {
        return switch (nivel != null ? nivel : "") {
            case "JUDICIAL"      -> 4;
            case "MORA_CRITICA"  -> 3;
            case "MORA_MEDIA"    -> 2;
            case "MORA_TEMPRANA" -> 1;
            default              -> 0;
        };
    }
}
