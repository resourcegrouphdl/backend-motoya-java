package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosFiadorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import com.motoyav2.contrato.domain.event.ContratoActivadoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Escucha ContratoActivadoEvent y crea automáticamente el caso de cobranza.
 *
 * Controlado por cobranzas.auto-iniciar-caso.enabled:
 *   false (default) → dry_run: solo loga, no persiste (para validar en prod sin riesgo)
 *   true            → modo real: llama IniciarCasoUseCase
 *
 * La bandera se activa en Cloud Run una vez verificado el funcionamiento en dry_run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IniciarCasoEventHandler {

    private final IniciarCasoUseCase iniciarCaso;

    @Value("${cobranzas.auto-iniciar-caso.enabled:false}")
    private boolean autoIniciarEnabled;

    @EventListener
    public void handle(ContratoActivadoEvent event) {
        if (!autoIniciarEnabled) {
            log.info("[AUTO-CASO] dry_run | contratoId={} titular={} {} — caso NO creado (bandera desactivada)",
                    event.contratoId(), event.titularNombres(), event.titularApellidos());
            return;
        }

        log.info("[AUTO-CASO] Iniciando caso para contratoId={} titular={} {}",
                event.contratoId(), event.titularNombres(), event.titularApellidos());

        IniciarCasoCommand command = new IniciarCasoCommand(
                event.contratoId(),
                event.storeId(),
                buildTitular(event),
                buildFiador(event),
                event.motoDescripcion(),
                event.capitalOriginal(),
                event.saldoActual(),
                "AL_DIA",
                "EN_SEGUIMIENTO",
                null,
                null,
                event.fechaVencimientoPrimerCuota(),
                buildCronograma(event),
                event.completadoPor() != null ? event.completadoPor() : "SISTEMA"
        );

        iniciarCaso.ejecutar(command)
                .subscribe(
                        caso -> log.info("[AUTO-CASO] Caso creado | contratoId={} clienteNombre={}",
                                caso.getContratoId(), caso.getClienteNombre()),
                        err  -> log.error("[AUTO-CASO] Error creando caso contratoId={}: {}",
                                event.contratoId(), err.getMessage())
                );
    }

    private DatosTitularDocument buildTitular(ContratoActivadoEvent e) {
        return DatosTitularDocument.builder()
                .nombres(e.titularNombres())
                .apellidos(e.titularApellidos())
                .tipoDocumento(e.titularTipoDocumento())
                .numeroDocumento(e.titularNumeroDocumento())
                .telefono(e.titularTelefono())
                .email(e.titularEmail())
                .direccion(e.titularDireccion())
                .distrito(e.titularDistrito())
                .provincia(e.titularProvincia())
                .departamento(e.titularDepartamento())
                .build();
    }

    private DatosFiadorDocument buildFiador(ContratoActivadoEvent e) {
        if (e.fiadorNumeroDocumento() == null) return null;
        return DatosFiadorDocument.builder()
                .nombres(e.fiadorNombres())
                .apellidos(e.fiadorApellidos())
                .tipoDocumento(e.fiadorTipoDocumento())
                .numeroDocumento(e.fiadorNumeroDocumento())
                .telefono(e.fiadorTelefono())
                .email(e.fiadorEmail())
                .parentesco(e.fiadorParentesco())
                .build();
    }

    private List<CuotaCronogramaDocument> buildCronograma(ContratoActivadoEvent e) {
        if (e.cronograma() == null) return List.of();
        return e.cronograma().stream()
                .map(c -> CuotaCronogramaDocument.builder()
                        .cuota(c.numeroCuota())
                        .cuotaNum(c.numeroCuota())
                        .fechaVencimiento(c.fechaVencimiento())
                        .monto(c.monto())
                        .estado(mapEstadoCuota(c.estado()))
                        .build())
                .toList();
    }

    private String mapEstadoCuota(String estadoPago) {
        if (estadoPago == null) return "PENDIENTE";
        return switch (estadoPago.toUpperCase()) {
            case "PAGADO", "PAGADA" -> "PAGADA";
            case "VENCIDO", "VENCIDA" -> "VENCIDA";
            default -> "PENDIENTE";
        };
    }
}
