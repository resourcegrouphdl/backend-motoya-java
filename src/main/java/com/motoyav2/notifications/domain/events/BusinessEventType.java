package com.motoyav2.notifications.domain.events;

/**
 * Tipos de eventos de negocio que pueden disparar notificaciones.
 * Mapea con los estados de EstadoContrato y EstadoPago del dominio financiero.
 */
public enum BusinessEventType {

    // ─── Contrato ─────────────────────────────────────────────────
    CONTRATO_GENERADO,         // EstadoContrato.CONTRATO_GENERADO → firma pendiente
    CONTRATO_FIRMADO,          // EstadoContrato.FIRMADO
    CONTRATO_ACTIVADO,         // EstadoContrato.ACTIVO
    CONTRATO_RECHAZADO,        // EstadoContrato.RECHAZADO
    CONTRATO_CANCELADO,        // EstadoContrato.CANCELADO
    CONTRATO_COMPLETADO,       // EstadoContrato.COMPLETADO

    // ─── Finanzas / Cobranza ───────────────────────────────────────
    CUOTA_POR_VENCER,          // EstadoPago.PROXIMO_VENCER (recordatorio 48h antes)
    CUOTA_VENCIDA,             // EstadoPago.VENCIDO
    PAGO_CONFIRMADO,           // Pago registrado exitosamente
    COMISION_GENERADA,         // Comisión de vendedor generada

    // ─── Eventos manuales desde API / otros microservicios ────────────
    MANUAL                     // Disparado manualmente desde el endpoint /api/v1/notifications
}
