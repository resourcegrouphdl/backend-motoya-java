package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.event.ContratoActivadoEvent;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import com.motoyav2.contrato.domain.model.EvidenciaDocumento;
import com.motoyav2.contrato.domain.port.in.CompletarContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletarContratoService implements CompletarContratoUseCase {

    private final ContratoRepository      contratoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Mono<Contrato> completar(String contratoId, String completadoPor) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    if (contrato.estado() != EstadoContrato.FIRMADO) {
                        return Mono.error(new BadRequestException(
                                "El contrato debe estar en estado FIRMADO. Estado actual: " + contrato.estado()));
                    }

                    if (contrato.numeroDeTitulo() == null || contrato.numeroDeTitulo().isBlank()) {
                        return Mono.error(new BadRequestException(
                                "Debe registrarse el número de título antes de completar el contrato"));
                    }

                    if (!esAprobado(contrato.tive())) {
                        return Mono.error(new BadRequestException("El documento TIVE debe estar APROBADO"));
                    }
                    if (!esAprobado(contrato.evidenciaSOAT())) {
                        return Mono.error(new BadRequestException("El documento SOAT debe estar APROBADO"));
                    }
                    if (!esAprobado(contrato.evidenciaPlacaRodaje())) {
                        return Mono.error(new BadRequestException("El documento de Placa de Rodaje debe estar APROBADO"));
                    }

                    ContratoStateMachine.validateTransition(contrato.estado(), EstadoContrato.COMPLETADO);

                    Instant now = Instant.now();
                    Contrato completado = new Contrato(
                            contrato.id(), contrato.numeroContrato(),
                            EstadoContrato.COMPLETADO, FaseContrato.FINALIZADO,
                            contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
                            contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
                            contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
                            contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
                            contrato.motivoRechazo(), contrato.fechaCreacion(), now, contrato.contratoParaImprimir(),
                            contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
                            contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actasDeEntrega()
                    );

                    return contratoRepository.save(completado)
                            .doOnNext(saved -> publicarEventoActivado(saved, completadoPor));
                });
    }

    private void publicarEventoActivado(Contrato c, String completadoPor) {
        try {
            var t  = c.titular();
            var f  = c.fiador();
            var df = c.datosFinancieros();

            String marca = c.facturaVehiculo() != null ? c.facturaVehiculo().marcaVehiculo()  : null;
            String modelo = c.facturaVehiculo() != null ? c.facturaVehiculo().modeloVehiculo() : null;
            Integer anio  = c.facturaVehiculo() != null ? c.facturaVehiculo().anioVehiculo()   : null;
            String moto = List.of(
                    marca  != null ? marca  : "",
                    modelo != null ? modelo : "",
                    anio   != null ? String.valueOf(anio) : ""
            ).stream().filter(s -> !s.isBlank()).reduce((a, b) -> a + " " + b).orElse("Moto");

            double capital = df != null && df.montoFinanciado() != null
                    ? df.montoFinanciado().doubleValue() : 0.0;

            List<CuotaCronograma> cuotas = c.cuotas() != null ? c.cuotas() : Collections.emptyList();
            List<ContratoActivadoEvent.CuotaActivadaDto> cronograma = cuotas.stream()
                    .map(cu -> new ContratoActivadoEvent.CuotaActivadaDto(
                            cu.numeroCuota() != null ? cu.numeroCuota() : 0,
                            cu.fechaVencimiento() != null
                                    ? DateTimeFormatter.ISO_LOCAL_DATE
                                        .withZone(ZoneId.of("America/Lima"))
                                        .format(cu.fechaVencimiento()) : null,
                            cu.montoCuota() != null ? cu.montoCuota().doubleValue() : 0.0,
                            cu.estadoPago() != null ? cu.estadoPago().name() : "PENDIENTE"
                    ))
                    .toList();

            String fechaPrimeraCuota = cuotas.stream()
                    .filter(cu -> cu.estadoPago() != null
                            && !"PAGADA".equalsIgnoreCase(cu.estadoPago().name()))
                    .min((a, b) -> {
                        if (a.fechaVencimiento() == null) return 1;
                        if (b.fechaVencimiento() == null) return -1;
                        return a.fechaVencimiento().compareTo(b.fechaVencimiento());
                    })
                    .map(cu -> cu.fechaVencimiento() != null
                            ? DateTimeFormatter.ISO_LOCAL_DATE
                                .withZone(ZoneId.of("America/Lima"))
                                .format(cu.fechaVencimiento()) : null)
                    .orElse(null);

            ContratoActivadoEvent event = new ContratoActivadoEvent(
                    c.id(),
                    c.tienda() != null ? c.tienda().tiendaId() : null,
                    t != null ? t.nombres() : null,
                    t != null ? t.apellidos() : null,
                    t != null ? t.tipoDocumento() : null,
                    t != null ? t.numeroDocumento() : null,
                    t != null ? t.telefono() : null,
                    t != null ? t.email() : null,
                    t != null ? t.direccion() : null,
                    t != null ? t.distrito() : null,
                    t != null ? t.provincia() : null,
                    t != null ? t.departamento() : null,
                    f != null ? f.nombres() : null,
                    f != null ? f.apellidos() : null,
                    f != null ? f.tipoDocumento() : null,
                    f != null ? f.numeroDocumento() : null,
                    f != null ? f.telefono() : null,
                    f != null ? f.email() : null,
                    f != null ? f.parentesco() : null,
                    moto,
                    capital,
                    capital,
                    fechaPrimeraCuota,
                    cronograma,
                    completadoPor
            );
            eventPublisher.publishEvent(event);
            log.info("[CONTRATO-COMPLETADO] ContratoActivadoEvent publicado | contratoId={}", c.id());
        } catch (Exception e) {
            log.error("[CONTRATO-COMPLETADO] Error construyendo evento para contratoId={}: {}", c.id(), e.getMessage());
        }
    }

    private boolean esAprobado(EvidenciaDocumento ev) {
        return ev != null && EstadoValidacion.APROBADO == ev.estadoValidacion();
    }
}
