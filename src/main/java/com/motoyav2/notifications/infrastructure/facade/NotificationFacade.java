package com.motoyav2.notifications.infrastructure.facade;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.notifications.domain.events.BusinessEventType;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.model.NotificationTemplate;
import com.motoyav2.notifications.domain.ports.in.PublishBusinessEventUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Fachada anti-corrupción entre el módulo de Contrato/Finanzas y el módulo de Notificaciones.
 *
 * Los servicios de aplicación (GenerarContratoPdfService, ConfirmarFirmaService, etc.)
 * llaman a esta fachada en lugar de depender directamente del módulo de notificaciones.
 *
 * Convenciones:
 *   - Número de teléfono: usar contrato.titular().telefono() (formato: 9XXXXXXXX sin país)
 *   - Email: usar contrato.titular().email()
 *   - Todos los errores de notificación son silenciados (.onErrorResume) para no
 *     bloquear el flujo principal de negocio.
 *
 * Ejemplo de uso en ConfirmarFirmaService:
 * <pre>
 *   return contratoRepository.save(firmado)
 *       .flatMap(saved -> finanzasIntegrationPort.iniciarFacturaDesdeContrato(saved)
 *           .onErrorResume(e -> Mono.empty())
 *           .then(notificationFacade.notificarContratoFirmado(saved)
 *               .onErrorResume(e -> Mono.empty()))
 *           .thenReturn(saved));
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFacade {

    private final PublishBusinessEventUseCase publishEvent;

    // ─── Eventos de Contrato ──────────────────────────────────────────────────

    /**
     * Disparar cuando: GenerarContratoPdfService → estado FIRMA_PENDIENTE
     * Notifica al titular que los documentos están listos para firmar.
     */
    public Mono<Void> notificarContratoListoParaFirma(Contrato contrato) {
        return publishEvent.publish(
                BusinessEventType.CONTRATO_GENERADO,
                contrato.id(),
                NotificationChannel.WHATSAPP,
                contrato.titular().telefono(),
                NotificationTemplate.CONTRATO_LISTO_PARA_FIRMA,
                Map.of(
                        "cliente", contrato.titular().nombreCompleto(),
                        "numeroContrato", contrato.numeroContrato(),
                        "cuotaMensual", formatMonto(contrato.datosFinancieros().cuotaMensual()),
                        "numeroCuotas", String.valueOf(contrato.datosFinancieros().numeroCuotas())
                )
        ).doOnError(ex -> log.error("[FACADE] Error publicando CONTRATO_GENERADO para {}: {}",
                contrato.id(), ex.getMessage()));
    }

    /**
     * Disparar cuando: ConfirmarFirmaService → estado FIRMADO
     * Notifica al titular la confirmación de la firma y el inicio del contrato.
     */
    public Mono<Void> notificarContratoFirmado(Contrato contrato) {
        return publishEvent.publish(
                BusinessEventType.CONTRATO_FIRMADO,
                contrato.id(),
                NotificationChannel.WHATSAPP,
                contrato.titular().telefono(),
                NotificationTemplate.CONTRATO_FIRMADO,
                Map.of(
                        "cliente", contrato.titular().nombreCompleto(),
                        "numeroContrato", contrato.numeroContrato(),
                        "tienda", contrato.tienda() != null ? contrato.tienda().nombreTienda() : "Motoya"
                )
        ).doOnError(ex -> log.error("[FACADE] Error publicando CONTRATO_FIRMADO para {}: {}",
                contrato.id(), ex.getMessage()));
    }

    /**
     * Disparar cuando: AprobarContratoService → estado ACTIVO
     * Email de bienvenida al titular.
     */
    public Mono<Void> notificarContratoActivado(Contrato contrato) {
        return publishEvent.publish(
                BusinessEventType.CONTRATO_ACTIVADO,
                contrato.id(),
                NotificationChannel.EMAIL,
                contrato.titular().email(),
                NotificationTemplate.CONTRATO_ACTIVADO,
                Map.of(
                        "cliente", contrato.titular().nombreCompleto(),
                        "numeroContrato", contrato.numeroContrato(),
                        "precioVehiculo", formatMonto(contrato.datosFinancieros().precioVehiculo()),
                        "numeroCuotas", String.valueOf(contrato.datosFinancieros().numeroCuotas()),
                        "cuotaMensual", formatMonto(contrato.datosFinancieros().cuotaMensual())
                )
        ).doOnError(ex -> log.error("[FACADE] Error publicando CONTRATO_ACTIVADO para {}: {}",
                contrato.id(), ex.getMessage()));
    }

    /**
     * Disparar cuando: RechazarContratoService → estado RECHAZADO
     * WhatsApp informando el rechazo y el motivo.
     */
    public Mono<Void> notificarContratoRechazado(Contrato contrato) {
        return publishEvent.publish(
                BusinessEventType.CONTRATO_RECHAZADO,
                contrato.id(),
                NotificationChannel.WHATSAPP,
                contrato.titular().telefono(),
                NotificationTemplate.CONTRATO_RECHAZADO,
                Map.of(
                        "cliente", contrato.titular().nombreCompleto(),
                        "motivo", contrato.motivoRechazo() != null
                                ? contrato.motivoRechazo()
                                : "Por favor contáctanos para más información"
                )
        ).doOnError(ex -> log.error("[FACADE] Error publicando CONTRATO_RECHAZADO para {}: {}",
                contrato.id(), ex.getMessage()));
    }

    /**
     * Disparar cuando: CompletarContratoService → estado COMPLETADO
     * Email de cierre de contrato.
     */
    public Mono<Void> notificarContratoCompletado(Contrato contrato) {
        return publishEvent.publish(
                BusinessEventType.CONTRATO_COMPLETADO,
                contrato.id(),
                NotificationChannel.EMAIL,
                contrato.titular().email(),
                NotificationTemplate.CONTRATO_COMPLETADO,
                Map.of(
                        "cliente", contrato.titular().nombreCompleto(),
                        "numeroContrato", contrato.numeroContrato()
                )
        ).doOnError(ex -> log.error("[FACADE] Error publicando CONTRATO_COMPLETADO para {}: {}",
                contrato.id(), ex.getMessage()));
    }

    // ─── Eventos de Finanzas / Cobranza ──────────────────────────────────────

    /**
     * Recordatorio de cuota próxima a vencer (llamar desde scheduler de cobranza 48h antes).
     *
     * @param contratoId ID del contrato
     * @param telefono   Teléfono del titular (9 dígitos sin país)
     * @param cliente    Nombre completo del titular
     * @param monto      Monto formateado (ej: "S/ 120.00")
     * @param fecha      Fecha de vencimiento formateada (ej: "10/03/2025")
     */
    public Mono<Void> notificarRecordatorioCuota(
            String contratoId, String telefono, String cliente, String monto, String fecha) {
        return publishEvent.publish(
                BusinessEventType.CUOTA_POR_VENCER,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.RECORDATORIO_CUOTA,
                Map.of("cliente", cliente, "monto", monto, "fecha", fecha)
        );
    }

    /**
     * Alerta de cuota vencida (llamar desde scheduler de cobranza cuando EstadoPago.VENCIDO).
     */
    public Mono<Void> notificarCuotaVencida(
            String contratoId, String telefono, String cliente, String monto, String diasVencido) {
        return publishEvent.publish(
                BusinessEventType.CUOTA_VENCIDA,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.CUOTA_VENCIDA,
                Map.of("cliente", cliente, "monto", monto, "diasVencido", diasVencido)
        );
    }

    /**
     * Confirmación de pago recibido.
     */
    public Mono<Void> notificarPagoConfirmado(
            String contratoId, String telefono, String cliente, String monto) {
        return publishEvent.publish(
                BusinessEventType.PAGO_CONFIRMADO,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.PAGO_CONFIRMADO,
                Map.of("cliente", cliente, "monto", monto)
        );
    }

    /**
     * Notificación de comisión disponible al vendedor (email).
     */
    public Mono<Void> notificarComisionDisponible(
            String contratoId, String emailVendedor, String vendedor, String monto) {
        return publishEvent.publish(
                BusinessEventType.COMISION_GENERADA,
                contratoId,
                NotificationChannel.EMAIL,
                emailVendedor,
                NotificationTemplate.COMISION_DISPONIBLE,
                Map.of("vendedor", vendedor, "monto", monto, "contratoId", contratoId)
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String formatMonto(java.math.BigDecimal monto) {
        if (monto == null) return "S/ 0.00";
        return "S/ " + monto.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
