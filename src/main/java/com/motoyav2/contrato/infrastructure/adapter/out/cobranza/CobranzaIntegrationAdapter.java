package com.motoyav2.contrato.infrastructure.adapter.out.cobranza;

import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosFiadorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.CuotaCronograma;
import com.motoyav2.contrato.domain.model.DatosFiador;
import com.motoyav2.contrato.domain.model.DatosTitular;
import com.motoyav2.contrato.domain.port.out.CobranzaIntegrationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Adaptador que conecta el módulo de Contratos con el módulo de Cobranzas.
 * Al aprobarse un contrato, inicia el caso de cobranza con el cronograma completo
 * y los datos del titular.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobranzaIntegrationAdapter implements CobranzaIntegrationPort {

    private final IniciarCasoUseCase iniciarCasoUseCase;

    @Override
    public Mono<Void> iniciarCasoDesdeContrato(Contrato contrato) {
        IniciarCasoCommand command = buildCommand(contrato);
        return iniciarCasoUseCase.ejecutar(command)
                .doOnSuccess(caso -> log.info("[Cobranza] Caso iniciado desde contrato — contratoId={}", contrato.id()))
                .doOnError(e -> log.error("[Cobranza] Error iniciando caso — contratoId={}: {}", contrato.id(), e.getMessage()))
                .then();
    }

    private IniciarCasoCommand buildCommand(Contrato contrato) {
        List<CuotaCronogramaDocument> cronograma = mapCronograma(contrato);

        String fechaPrimeraCuota = cronograma.stream()
                .map(CuotaCronogramaDocument::getFechaVencimiento)
                .filter(f -> f != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        double cuotaMensual = contrato.datosFinancieros().cuotaMensual().doubleValue();
        int numeroCuotas   = contrato.datosFinancieros().numeroCuotas();
        double capitalOriginal = cuotaMensual * numeroCuotas;

        DatosTitular t = contrato.titular();
        String descripcion = t.nombreCompleto().toUpperCase() + " " + t.telefono()
                + " / " + contrato.fiador().nombreCompleto().toUpperCase() + " " + contrato.fiador().telefono();

        return new IniciarCasoCommand(
                contrato.id(),
                contrato.tienda().tiendaId(),
                mapTitular(t),
                mapFiador(contrato.fiador()),
                descripcion,
                capitalOriginal,
                capitalOriginal,        // saldoActual = capitalOriginal al inicio
                "MORA_TEMPRANA",
                "EN_SEGUIMIENTO",
                null,                   // agenteAsignadoId — se asigna luego
                null,                   // agenteAsignadoNombre
                fechaPrimeraCuota,
                cronograma,
                contrato.creadoPor()
        );
    }

    private List<CuotaCronogramaDocument> mapCronograma(Contrato contrato) {
        return contrato.cuotas().stream()
                .map(c -> CuotaCronogramaDocument.builder()
                        .cuota(c.numeroCuota())
                        .cuotaNum(c.numeroCuota())
                        .fechaVencimiento(c.fechaVencimiento()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .toString())
                        .monto(contrato.datosFinancieros().cuotaMensual().doubleValue())
                        .estado("PENDIENTE")
                        .build())
                .toList();
    }

    private DatosTitularDocument mapTitular(DatosTitular t) {
        return DatosTitularDocument.builder()
                .nombres(t.nombres())
                .apellidos(t.apellidos())
                .tipoDocumento(t.tipoDocumento())
                .numeroDocumento(t.numeroDocumento())
                .telefono(t.telefono())
                .email(t.email())
                .direccion(t.direccion())
                .distrito(t.distrito())
                .provincia(t.provincia())
                .departamento(t.departamento())
                .build();
    }

    private DatosFiadorDocument mapFiador(DatosFiador f) {
        if (f == null) return null;
        return DatosFiadorDocument.builder()
                .nombres(f.nombres())
                .apellidos(f.apellidos())
                .tipoDocumento(f.tipoDocumento())
                .numeroDocumento(f.numeroDocumento())
                .telefono(f.telefono())
                .email(f.email())
                .parentesco(f.parentesco())
                .build();
    }
}