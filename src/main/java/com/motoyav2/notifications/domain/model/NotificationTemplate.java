package com.motoyav2.notifications.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Catálogo de plantillas. Agregar aquí cada nueva plantilla de negocio.
 * templateName → nombre del archivo en notification-templates/{channel}/{templateName}.{ext}
 * defaultChannel → canal preferido para esta plantilla
 * emailSubject  → asunto de email (ignorado en WhatsApp/SMS)
 */
@Getter
@RequiredArgsConstructor
public enum NotificationTemplate {

  // ─── Contrato ────────────────────────────────────────────────────────────
  CONTRATO_LISTO_PARA_FIRMA(
      "contrato-listo-firma",
      NotificationChannel.WHATSAPP,
      "Contrato listo para firma - Motoya"),

  CONTRATO_FIRMADO(
      "contrato-firmado",
      NotificationChannel.WHATSAPP,
      "Contrato firmado con éxito - Motoya"),

  CONTRATO_ACTIVADO(
      "contrato-activado",
      NotificationChannel.EMAIL,
      "¡Tu contrato está activo! - Motoya"),

  CONTRATO_RECHAZADO(
      "contrato-rechazado",
      NotificationChannel.WHATSAPP,
      "Actualización sobre tu contrato - Motoya"),

  CONTRATO_CANCELADO(
      "contrato-cancelado",
      NotificationChannel.WHATSAPP,
      "Contrato cancelado - Motoya"),

  CONTRATO_COMPLETADO(
      "contrato-completado",
      NotificationChannel.EMAIL,
      "¡Tu contrato ha sido completado! - Motoya"),

  // ─── Finanzas / Cobranza ─────────────────────────────────────────────────
  RECORDATORIO_CUOTA(
      "recordatorio-cuota",
      NotificationChannel.WHATSAPP,
      "Recordatorio de pago - Motoya"),

  CUOTA_VENCIDA(
      "cuota-vencida",
      NotificationChannel.WHATSAPP,
      "Cuota vencida - Motoya"),

  PAGO_CONFIRMADO(
      "pago-confirmado",
      NotificationChannel.WHATSAPP,
      "Pago confirmado - Motoya"),

  COMISION_DISPONIBLE(
      "comision-disponible",
      NotificationChannel.EMAIL,
      "Nueva comisión disponible - Motoya"),

  // ─── Evaluación De credito / notificaciones ─────────────────────────────────────────────────

  CREDITO_APROBADO_NOTIFICACION(
      "credito_aprobado_notificacion",
      NotificationChannel.WHATSAPP,
      "¡Tu crédito ha sido aprobado! - Motoya"),

  CREDITO_MOTO_FIADOR_NOTIFICACION(
      "credito_moto_fiador_notificacion",
      NotificationChannel.WHATSAPP,
      "Notificación de fiador - Motoya"),

  CREDITO_MOTO_SOLICITUD_RECIBIDA(
      "credito_moto_solicitud_recibida",
      NotificationChannel.WHATSAPP,
      "Solicitud de crédito recibida - Motoya"),

  CREDITO_NOTIFICACION_REFERENCIA(
      "credito_notificacion_referencia",
      NotificationChannel.WHATSAPP,
      "Notificación de referencia personal - Motoya");


  /**
   * Nombre del archivo de plantilla (sin extensión ni canal).
   */
  private final String templateName;
  /**
   * Canal por defecto cuando no se especifica uno explícito.
   */
  private final NotificationChannel defaultChannel;
  /**
   * Asunto del correo electrónico (solo aplica para EMAIL).
   */
  private final String emailSubject;
}
