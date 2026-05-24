package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import com.motoyav2.notifications.domain.model.NotificationTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mapeo entre NotificationTemplate interno y el nombre de template registrado en Meta Business Manager.
 *
 * Cada entrada define:
 *   - metaName     → nombre exacto del template aprobado en Meta (solo minúsculas y guiones bajos)
 *   - languageCode → código de idioma del template (usualmente "es")
 *   - paramSlots   → nombres de variables en el ORDEN POSICIONAL que Meta espera ({{1}}, {{2}}, ...)
 *
 * Para registrar un nuevo template:
 *   1. Crear/aprobar el template en Meta Business Manager con el nombre indicado.
 *   2. Agregar la entrada en el bloque static abajo.
 *   3. No es necesario reiniciar — el map se construye al arrancar la app.
 */
@Component
public class MetaTemplateRegistry {

    public record MetaTemplateConfig(String metaName, String languageCode, List<String> paramSlots) {}

    private static final Map<NotificationTemplate, MetaTemplateConfig> REGISTRY =
            new EnumMap<>(NotificationTemplate.class);

    static {
        // ─── Evaluación ───────────────────────────────────────────────────────
        reg(NotificationTemplate.CREDITO_MOTO_SOLICITUD_RECIBIDA,
                "motoya_solicitud_recibida",
                List.of("cliente", "numero-solicitud"));

        reg(NotificationTemplate.CREDITO_APROBADO_NOTIFICACION,
                "motoya_credito_aprobado",
                List.of("cliente", "certificado", "certificadoUrl"));

        reg(NotificationTemplate.CREDITO_MOTO_FIADOR_NOTIFICACION,
                "motoya_credito_aprobado_fiador",
                List.of("fiador", "cliente", "certificado", "certificadoUrl"));

        reg(NotificationTemplate.CREDITO_NOTIFICACION_REFERENCIA,
                "motoya_verificacion_referencia",
                List.of("nombreRef", "titular"));

        reg(NotificationTemplate.CREDITO_MOTO_ENTREVISTA_WHATSAPP,
                "motoya_entrevista_solicitud",
                List.of("cliente", "codigoDeSolicitud"));

        reg(NotificationTemplate.SOLICITUD_CAMBIO_ESTADO_VENDEDOR,
                "motoya_cambio_estado_vendedor",
                List.of("vendedor", "cliente", "codigoDeSolicitud", "estado", "motivo"));

        // ─── Contrato ─────────────────────────────────────────────────────────
        reg(NotificationTemplate.CONTRATO_LISTO_PARA_FIRMA,
                "motoya_contrato_listo_firma",
                List.of("cliente", "numeroContrato", "cuotaMensual", "numeroCuotas"));

        reg(NotificationTemplate.CONTRATO_FIRMADO,
                "motoya_contrato_firmado",
                List.of("cliente", "numeroContrato", "tienda"));

        reg(NotificationTemplate.CONTRATO_RECHAZADO,
                "motoya_contrato_rechazado",
                List.of("cliente", "motivo"));

        // ─── Cobranza ─────────────────────────────────────────────────────────
        reg(NotificationTemplate.RECORDATORIO_CUOTA,
                "motoya_recordatorio_cuota",
                List.of("cliente", "monto", "fecha"));

        reg(NotificationTemplate.CUOTA_VENCIDA,
                "motoya_cuota_vencida",
                List.of("cliente", "monto", "diasVencido", "montoMora", "montoTotal"));

        reg(NotificationTemplate.PROMESA_RECORDATORIO,
                "motoya_promesa_recordatorio",
                List.of("cliente", "monto"));

        reg(NotificationTemplate.AUTORESPUESTA_COBRANZA,
                "motoya_autorespuesta_cobranza",
                List.of("cliente"));

        reg(NotificationTemplate.VOUCHER_RECIBIDO_COBRANZA,
                "motoya_voucher_recibido",
                List.of("cliente", "banco", "monto"));

        reg(NotificationTemplate.PAGO_CONFIRMADO,
                "motoya_pago_confirmado",
                List.of("cliente", "monto"));

        // ─── Finanzas ─────────────────────────────────────────────────────────
        reg(NotificationTemplate.PAGO_COMISION_WHATSAPP,
                "motoya_pago_comision_vendedor",
                List.of("vendedor", "periodo", "monto", "comprobanteUrl"));

        reg(NotificationTemplate.PAGO_FACTURA_TIENDA_WHATSAPP,
                "motoya_pago_factura_tienda",
                List.of("tiendaNombre", "numeroFactura", "clienteNombre", "concepto", "monto", "fechaPago", "metodoPago"));
    }

    private static void reg(NotificationTemplate template, String metaName, List<String> slots) {
        REGISTRY.put(template, new MetaTemplateConfig(metaName, "es_PE", slots));
    }

    public Optional<MetaTemplateConfig> find(NotificationTemplate template) {
        return Optional.ofNullable(REGISTRY.get(template));
    }
}