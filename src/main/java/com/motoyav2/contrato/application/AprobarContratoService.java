package com.motoyav2.contrato.application;

import com.motoyav2.contrato.application.applicatioMapper.ContratoParaDescargasMaper;
import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import com.motoyav2.contrato.domain.port.out.CobranzaIntegrationPort;
import com.motoyav2.contrato.domain.port.out.CrearEventoEnCalendar;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.contrato.domain.port.in.AprobarContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.port.out.ObtenerRucDeStoreUseCase;
import com.motoyav2.contrato.domain.service.CuotaCronogramaCliente;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AprobarContratoService implements AprobarContratoUseCase {

  private final ContratoRepository contratoRepository;
  private final CuotaCronogramaCliente cuotaCronogramaCliente;
  private final ContratoParaDescargasMaper contratoParaDescargasMaper;
  private final ObtenerRucDeStoreUseCase obtenerRucDeStoreUseCase;
  private final CrearEventoEnCalendar crearEventoEnCalendar;
  private final CobranzaIntegrationPort cobranzaIntegrationPort;


  @Override
  public Mono<Contrato> aprobar(String contratoId, String aprobadoPor) {
    return contratoRepository.findById(contratoId)
        .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
        .flatMap(contrato -> {
          if (contrato.estado() != EstadoContrato.EN_VALIDACION) {
            return Mono.error(new BadRequestException(
                "El contrato debe estar EN_VALIDACION para ser aprobado. Estado actual: " + contrato.estado()));
          }

          boolean algoBoucherAprobado = contrato.boucheresPagoInicial() != null
              && contrato.boucheresPagoInicial().stream()
                    .anyMatch(b -> b.estadoValidacion() == EstadoValidacion.APROBADO);
          if (!algoBoucherAprobado) {
            return Mono.error(new BadRequestException("Al menos un boucher de pago inicial debe estar aprobado"));
          }
          if (contrato.facturaVehiculo() == null
              || contrato.facturaVehiculo().estadoValidacion() != EstadoValidacion.APROBADO) {
            return Mono.error(new BadRequestException("La factura del vehículo debe estar aprobada"));
          }

          ContratoStateMachine.validateTransition(contrato.estado(), EstadoContrato.GENERANDO_CONTRATO);

          Contrato enGeneracion = new Contrato(
              contrato.id(), contrato.numeroContrato(),
              EstadoContrato.GENERANDO_CONTRATO, FaseContrato.GENERACION_CONTRATO,
              contrato.titular(), contrato.fiador(), contrato.tienda(), contrato.datosFinancieros(),
              contrato.boucheresPagoInicial(), contrato.facturaVehiculo(),
              contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
              contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
              contrato.motivoRechazo(), contrato.fechaCreacion(), Instant.now(),
              contrato.contratoParaImprimir(),
              contrato.numeroDeTitulo(), contrato.fechaRegistroTitulo(),
              contrato.tive(), contrato.evidenciaSOAT(), contrato.evidenciaPlacaRodaje(), contrato.actasDeEntrega()
          );

          return contratoRepository.save(enGeneracion)
              .flatMap(saved -> obtenerRucDeStoreUseCase.obtenerRucDeStore(saved.tienda().tiendaId())
                  .defaultIfEmpty("")
                  .flatMap(ruc -> {
                    List<CuotaCronograma> cuotas = cuotaCronogramaCliente.generarCronograma(
                        saved.facturaVehiculo().fechaEmision(),
                        saved.datosFinancieros().cuotaMensual(),
                        saved.datosFinancieros().numeroCuotas()
                    );

                    ContratoParaImprimir contratoParaImprimir = contratoParaDescargasMaper.contratoParaImprimir(saved, ruc);

                    Contrato contratoGenerado = new Contrato(
                        saved.id(), saved.numeroContrato(),
                        EstadoContrato.CONTRATO_GENERADO, FaseContrato.GENERACION_CONTRATO,
                        saved.titular(), saved.fiador(), saved.tienda(), saved.datosFinancieros(),
                        saved.boucheresPagoInicial(), saved.facturaVehiculo(),
                        cuotas, List.of(), saved.evidenciasFirma(),
                        saved.notificaciones(), saved.creadoPor(), saved.evaluacionId(),
                        saved.motivoRechazo(), saved.fechaCreacion(), Instant.now(),
                        contratoParaImprimir,
                        saved.numeroDeTitulo(), saved.fechaRegistroTitulo(),
                        saved.tive(), saved.evidenciaSOAT(), saved.evidenciaPlacaRodaje(), saved.actasDeEntrega()
                    );

                    // Persiste el contrato con el cronograma completo y luego dispara,
                    // en paralelo e independientes:
                    //   1. Cobranzas: inicia el caso de cobro (principal, fallo logeado pero no bloquea)
                    //   2. Calendar: escribe el cronograma como respaldo (fallo logeado pero no bloquea)
                    return contratoRepository.save(contratoGenerado)
                        .flatMap(persistido -> {
                            Mono<Void> iniciarCobranza = cobranzaIntegrationPort
                                .iniciarCasoDesdeContrato(persistido)
                                .onErrorResume(e -> {
                                    log.error("[Cobranza] Error iniciando caso para contratoId={}: {}",
                                        persistido.id(), e.getMessage());
                                    return Mono.empty();
                                });

                            Mono<Void> respaldarCalendar = crearEventoEnCalendar
                                .crearEventoEnCalendar(persistido)
                                .doOnSuccess(v -> log.info(
                                    "[Calendar] Eventos de respaldo creados para contratoId={}", persistido.id()))
                                .onErrorResume(e -> {
                                    log.error("[Calendar] Error creando respaldo en Calendar para contratoId={}: {}",
                                        persistido.id(), e.getMessage());
                                    return Mono.empty();
                                });

                            return Mono.when(iniciarCobranza, respaldarCalendar)
                                .thenReturn(persistido);
                        });
                  }));

        });
  }
}
