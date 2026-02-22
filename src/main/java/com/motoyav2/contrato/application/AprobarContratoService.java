package com.motoyav2.contrato.application;

import com.motoyav2.contrato.application.applicatioMapper.ContratoParaDescargasMaper;
import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import com.motoyav2.contrato.domain.service.ContratoStateMachine;
import com.motoyav2.contrato.domain.port.in.AprobarContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.service.CuotaCronogramaCliente;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AprobarContratoService implements AprobarContratoUseCase {

  private final ContratoRepository contratoRepository;
  private final CuotaCronogramaCliente cuotaCronogramaCliente;
  private final ContratoParaDescargasMaper  contratoParaDescargasMaper;


  @Override
  public Mono<Contrato> aprobar(String contratoId, String aprobadoPor) {
    return contratoRepository.findById(contratoId)
        .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
        .flatMap(contrato -> {
          if (contrato.estado() != EstadoContrato.EN_VALIDACION) {
            return Mono.error(new BadRequestException(
                "El contrato debe estar EN_VALIDACION para ser aprobado. Estado actual: " + contrato.estado()));
          }

          if (contrato.boucherPagoInicial() == null
              || contrato.boucherPagoInicial().estadoValidacion() != EstadoValidacion.APROBADO) {
            return Mono.error(new BadRequestException("El boucher de pago inicial debe estar aprobado"));
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
              contrato.boucherPagoInicial(), contrato.facturaVehiculo(),
              contrato.cuotas(), contrato.documentosGenerados(), contrato.evidenciasFirma(),
              contrato.notificaciones(), contrato.creadoPor(), contrato.evaluacionId(),
              contrato.motivoRechazo(), contrato.fechaCreacion(), Instant.now(),
              contrato.contratoParaImprimir()
          );

          return contratoRepository.save(enGeneracion)
              .flatMap(saved -> {
                List<CuotaCronograma> cuotas = cuotaCronogramaCliente.generarCronograma(
                    saved.facturaVehiculo().fechaEmision(),
                    saved.datosFinancieros().cuotaMensual(),
                    saved.datosFinancieros().numeroCuotas()
                );

                ContratoParaImprimir contratoParaImprimir = contratoParaDescargasMaper.contratoParaImprimir(saved);

                Contrato contratoGenereado = new Contrato(
                    saved.id(), saved.numeroContrato(),
                    EstadoContrato.CONTRATO_GENERADO, FaseContrato.GENERACION_CONTRATO,
                    saved.titular(), saved.fiador(), saved.tienda(), saved.datosFinancieros(),
                    saved.boucherPagoInicial(), saved.facturaVehiculo(),
                    cuotas, List.of(), saved.evidenciasFirma(),
                    saved.notificaciones(), saved.creadoPor(), saved.evaluacionId(),
                    saved.motivoRechazo(), saved.fechaCreacion(), Instant.now(),
                    contratoParaImprimir
                );
                return contratoRepository.save(contratoGenereado);
              });

        });
  }
}
