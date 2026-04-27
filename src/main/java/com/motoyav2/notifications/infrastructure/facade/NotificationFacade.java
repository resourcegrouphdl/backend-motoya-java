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

    // ─── Eventos de Evaluación ────────────────────────────────────────────────

    /**
     * Disparar cuando: IngresarSolicitudUseCase → solicitud guardada en Firestore.
     *
     * Envía de forma independiente (fire-and-forget por el Outbox):
     *   1. WhatsApp al titular — confirmación de solicitud recibida
     *   2. Email al titular — ídem por correo
     *   3. Email al fiador  — confirmación de registro como garante (solo si hay fiador con email)
     *   4. Email al vendedor — aviso de nueva solicitud registrada
     *
     * Un fallo en uno no cancela los demás. Todos van vía Outbox (no sincrónicos).
     */
    public Mono<Void> notificarSolicitudIngresada(
            String solicitudId,
            String emailTitular,    String nombreTitular,
            String emailFiador,     String nombreFiador,
            String emailVendedor,   String nombreVendedor,
            String modeloMoto,      String montoSolicitado,
            String documentoTitular, String telefonoTitular,
            String fechaRegistro,   String codigoDeSolicitud) {

        Map<String, String> varsTitular = Map.of(
                "cliente", nombreTitular != null ? nombreTitular : "");

        Map<String, String> varsWaTitular = Map.of(
                "cliente",          nombreTitular      != null ? nombreTitular      : "",
                "numero-solicitud", codigoDeSolicitud  != null ? codigoDeSolicitud  : solicitudId);

        Map<String, String> varsFiador = (nombreFiador != null && nombreTitular != null)
                ? Map.of("fiador", nombreFiador, "cliente", nombreTitular)
                : Map.of("fiador", nombreFiador != null ? nombreFiador : "",
                         "cliente", nombreTitular != null ? nombreTitular : "");

        Map<String, String> varsVendedor = Map.of(
                "vendedor",      nombreVendedor       != null ? nombreVendedor       : "",
                "cliente",       nombreTitular        != null ? nombreTitular        : "",
                "documento",     documentoTitular     != null ? documentoTitular     : "",
                "telefono",      telefonoTitular      != null ? telefonoTitular      : "",
                "modeloMoto",    modeloMoto           != null ? modeloMoto           : "",
                "monto",         montoSolicitado      != null ? montoSolicitado      : "",
                "fiador",        nombreFiador         != null ? nombreFiador         : "No registrado",
                "fechaRegistro", fechaRegistro        != null ? fechaRegistro        : "");

        Mono<Void> waTitular = (telefonoTitular != null && !telefonoTitular.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.SOLICITUD_INGRESADA, solicitudId,
                        NotificationChannel.WHATSAPP, telefonoTitular,
                        NotificationTemplate.CREDITO_MOTO_SOLICITUD_RECIBIDA, varsWaTitular)
                  .onErrorResume(e -> { log.warn("[NotifFacade] WA titular solicitud error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Mono<Void> mailTitular = esEmailValido(emailTitular)
                ? publishEvent.publish(
                        BusinessEventType.SOLICITUD_INGRESADA, solicitudId,
                        NotificationChannel.EMAIL, emailTitular,
                        NotificationTemplate.CREDITO_MOTO_SOLICITUD_RECIBIDA_EMAIL, varsTitular)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email titular solicitud error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Mono<Void> mailFiador = esEmailValido(emailFiador)
                ? publishEvent.publish(
                        BusinessEventType.SOLICITUD_INGRESADA, solicitudId,
                        NotificationChannel.EMAIL, emailFiador,
                        NotificationTemplate.CREDITO_MOTO_FIADOR_SOLICITUD, varsFiador)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email fiador solicitud error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Mono<Void> mailVendedor = esEmailValido(emailVendedor)
                ? publishEvent.publish(
                        BusinessEventType.SOLICITUD_INGRESADA, solicitudId,
                        NotificationChannel.EMAIL, emailVendedor,
                        NotificationTemplate.SOLICITUD_NUEVA_VENDEDOR, varsVendedor)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email vendedor solicitud error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Map<String, String> varsEntrevista = Map.of(
                "cliente",           nombreTitular     != null ? nombreTitular     : "",
                "codigoDeSolicitud", codigoDeSolicitud != null ? codigoDeSolicitud : solicitudId);

        Mono<Void> mailEntrevistaTitular = esEmailValido(emailTitular)
                ? publishEvent.publish(
                        BusinessEventType.SOLICITUD_INGRESADA, solicitudId,
                        NotificationChannel.EMAIL, emailTitular,
                        NotificationTemplate.CREDITO_MOTO_ENTREVISTA_EMAIL, varsEntrevista)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email entrevista titular error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        return Mono.when(waTitular, mailTitular, mailFiador, mailVendedor, mailEntrevistaTitular);
    }

    /**
     * Notifica a titular y fiador (si existe) cuando la decisión final es APROBADO.
     * Se envía:
     *   - WhatsApp + Email al titular
     *   - WhatsApp al fiador (si hay fiador)
     * Todos los envíos son independientes; un fallo en uno no cancela los demás.
     *
     * @param solicitudId        ID de la solicitud (referencia del evento)
     * @param telefonoTitular    Teléfono del titular (9 dígitos sin país)
     * @param emailTitular       Email del titular
     * @param nombreTitular      Nombre completo del titular
     * @param telefonoFiador     Teléfono del fiador (null si no hay fiador)
     * @param nombreFiador       Nombre completo del fiador (null si no hay fiador)
     * @param codigoCertificado  Código/número del certificado (ej: codigoDeSolicitud)
     * @param urlCertificado     URL pública del certificado PNG
     */
    public Mono<Void> notificarCreditoAprobado(
            String solicitudId,
            String telefonoTitular, String emailTitular, String nombreTitular,
            String telefonoFiador, String emailFiador, String nombreFiador,
            String codigoCertificado, String urlCertificado) {

        Map<String, String> varsTitular = Map.of(
                "cliente",        nombreTitular   != null ? nombreTitular   : "",
                "certificado",    codigoCertificado != null ? codigoCertificado : "",
                "certificadoUrl", urlCertificado  != null ? urlCertificado  : ""
        );

        Mono<Void> waTitular = (telefonoTitular != null && !telefonoTitular.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.CREDITO_APROBADO, solicitudId,
                        NotificationChannel.WHATSAPP, telefonoTitular,
                        NotificationTemplate.CREDITO_APROBADO_NOTIFICACION, varsTitular)
                  .onErrorResume(e -> { log.warn("[NotifFacade] WA titular aprobado error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Mono<Void> mailTitular = esEmailValido(emailTitular)
                ? publishEvent.publish(
                        BusinessEventType.CREDITO_APROBADO, solicitudId,
                        NotificationChannel.EMAIL, emailTitular,
                        NotificationTemplate.CREDITO_APROBADO_NOTIFICACION, varsTitular)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email titular aprobado error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Map<String, String> varsFiadorAprobado = Map.of(
                "fiador",         nombreFiador      != null ? nombreFiador      : "",
                "cliente",        nombreTitular     != null ? nombreTitular     : "",
                "certificado",    codigoCertificado != null ? codigoCertificado : "",
                "certificadoUrl", urlCertificado    != null ? urlCertificado    : ""
        );

        Mono<Void> waFiador = (telefonoFiador != null && !telefonoFiador.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.CREDITO_APROBADO, solicitudId,
                        NotificationChannel.WHATSAPP, telefonoFiador,
                        NotificationTemplate.CREDITO_MOTO_FIADOR_NOTIFICACION, varsFiadorAprobado)
                  .onErrorResume(e -> { log.warn("[NotifFacade] WA fiador aprobado error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        Mono<Void> mailFiador = esEmailValido(emailFiador)
                ? publishEvent.publish(
                        BusinessEventType.CREDITO_APROBADO, solicitudId,
                        NotificationChannel.EMAIL, emailFiador,
                        NotificationTemplate.CREDITO_MOTO_FIADOR_NOTIFICACION, varsFiadorAprobado)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email fiador aprobado error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();

        return Mono.when(waTitular, mailTitular, waFiador, mailFiador);
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
     * Alerta de cuota vencida con mora calculada (S/ 3.00/día desde el día 1).
     *
     * @param montoMora  Mora acumulada formateada (ej: "S/ 9.00")
     * @param montoTotal Cuota + mora formateada (ej: "S/ 129.00")
     */
    public Mono<Void> notificarCuotaVencida(
            String contratoId, String telefono, String cliente,
            String monto, String diasVencido, String montoMora, String montoTotal) {
        return publishEvent.publish(
                BusinessEventType.CUOTA_VENCIDA,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.CUOTA_VENCIDA,
                Map.of("cliente", cliente, "monto", monto,
                       "diasVencido", diasVencido,
                       "montoMora", montoMora,
                       "montoTotal", montoTotal)
        );
    }

    /**
     * Recordatorio al cliente en el día que prometió pagar.
     */
    public Mono<Void> notificarRecordatorioPromesa(
            String contratoId, String telefono, String cliente, String monto) {
        return publishEvent.publish(
                BusinessEventType.CUOTA_VENCIDA,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.PROMESA_RECORDATORIO,
                Map.of("cliente", cliente, "monto", monto)
        ).onErrorResume(e -> {
            log.warn("[FACADE] Error recordatorio promesa contratoId={}: {}", contratoId, e.getMessage());
            return Mono.empty();
        });
    }

    /**
     * Autorespuesta al cliente cuando envía un mensaje de texto a este chat de cobranza.
     * Informa que el chat es solo notificaciones y da el número del asesor.
     */
    public Mono<Void> notificarAutorespuestaCobranza(
            String contratoId, String telefono, String cliente) {
        return publishEvent.publish(
                BusinessEventType.PAYMENT_PROOF_RECEIVED,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.AUTORESPUESTA_COBRANZA,
                Map.of("cliente", cliente)
        ).onErrorResume(e -> {
            log.warn("[FACADE] Error autorespuesta cobranza contratoId={}: {}", contratoId, e.getMessage());
            return Mono.empty();
        });
    }

    /**
     * Confirma al cliente que se recibió su comprobante de pago vía WhatsApp.
     *
     * @param banco  Nombre del banco detectado por OCR (ej: "BCP")
     * @param monto  Monto detectado formateado (ej: "S/ 120.00")
     */
    public Mono<Void> notificarVoucherRecibidoCobranza(
            String contratoId, String telefono, String cliente, String banco, String monto) {
        return publishEvent.publish(
                BusinessEventType.PAYMENT_PROOF_RECEIVED,
                contratoId,
                NotificationChannel.WHATSAPP,
                telefono,
                NotificationTemplate.VOUCHER_RECIBIDO_COBRANZA,
                Map.of("cliente", cliente,
                       "banco", banco != null ? banco : "No identificado",
                       "monto", monto != null ? monto : "Por determinar")
        ).onErrorResume(e -> {
            log.warn("[FACADE] Error notificando voucher recibido contratoId={}: {}", contratoId, e.getMessage());
            return Mono.empty();
        });
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

    /**
     * Notifica al vendedor (WhatsApp + Email) cuando se confirma el pago de su comisión quincena.
     * Envía el link del PDF comprobante generado.
     * Ambos envíos se hacen de forma independiente; un fallo en uno no cancela el otro.
     */
    public Mono<Void> notificarPagoComisionConfirmado(
            String pagoId, String email, String telefono, String vendedor,
            String periodo, String monto, String comprobanteUrl) {
        String refId = pagoId != null ? pagoId : "pago-desconocido";
        Map<String, String> vars = Map.of(
                "vendedor",       vendedor       != null ? vendedor       : "",
                "periodo",        periodo        != null ? periodo        : "",
                "monto",          "S/ " + (monto != null ? monto : "0"),
                "comprobanteUrl", comprobanteUrl != null ? comprobanteUrl : ""
        );
        Mono<Void> wa = (telefono != null && !telefono.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.COMISION_PAGADA, refId,
                        NotificationChannel.WHATSAPP, telefono,
                        NotificationTemplate.PAGO_COMISION_WHATSAPP, vars)
                  .onErrorResume(e -> { log.warn("[NotifFacade] WA pago comisión error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();
        Mono<Void> mail = (email != null && !email.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.COMISION_PAGADA, refId,
                        NotificationChannel.EMAIL, email,
                        NotificationTemplate.PAGO_COMISION_EMAIL, vars)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email pago comisión error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();
        return Mono.when(wa, mail);
    }

    /**
     * Notifica a la tienda (WhatsApp + Email) cuando Motoya Digital registra un pago
     * de factura (INICIAL o SALDO). Ambos envíos son independientes y fire-and-forget.
     *
     * @param facturaId    ID de la factura (referencia del evento)
     * @param email        Email de contacto de la tienda
     * @param telefono     Teléfono de la tienda (9 dígitos sin país)
     * @param tiendaNombre Nombre de la tienda
     * @param numeroFactura Número de factura (ej: "F002-00005444")
     * @param clienteNombre Nombre del cliente titular del contrato
     * @param concepto     "Pago Inicial" o "Pago de Saldo"
     * @param monto        Monto formateado (ej: "S/ 1,500.00")
     * @param fechaPago    Fecha en que se realizó el pago (ej: "2026-03-30")
     * @param metodoPago   Método de pago (ej: "TRANSFERENCIA")
     */
    public Mono<Void> notificarPagoFacturaTienda(
            String facturaId, String email, String telefono, String tiendaNombre,
            String numeroFactura, String clienteNombre, String concepto,
            String monto, String fechaPago, String metodoPago) {
        String refId = facturaId != null ? facturaId : "factura-desconocida";
        Map<String, String> vars = Map.of(
                "tiendaNombre",  tiendaNombre  != null ? tiendaNombre  : "",
                "numeroFactura", numeroFactura != null ? numeroFactura : "",
                "clienteNombre", clienteNombre != null ? clienteNombre : "",
                "concepto",      concepto      != null ? concepto      : "",
                "monto",         monto         != null ? monto         : "",
                "fechaPago",     fechaPago     != null ? fechaPago     : "",
                "metodoPago",    metodoPago    != null ? metodoPago    : ""
        );
        Mono<Void> wa = (telefono != null && !telefono.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.FACTURA_PAGO_REGISTRADO, refId,
                        NotificationChannel.WHATSAPP, telefono,
                        NotificationTemplate.PAGO_FACTURA_TIENDA_WHATSAPP, vars)
                  .onErrorResume(e -> { log.warn("[NotifFacade] WA pago factura error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();
        Mono<Void> mail = (email != null && !email.isBlank())
                ? publishEvent.publish(
                        BusinessEventType.FACTURA_PAGO_REGISTRADO, refId,
                        NotificationChannel.EMAIL, email,
                        NotificationTemplate.PAGO_FACTURA_TIENDA_EMAIL, vars)
                  .onErrorResume(e -> { log.warn("[NotifFacade] Email pago factura error: {}", e.getMessage()); return Mono.empty(); })
                : Mono.empty();
        return Mono.when(wa, mail);
    }

    /**
     * Notifica al vendedor por WhatsApp cuando cambia el estado de su solicitud.
     * Solo para estados relevantes para el vendedor (aprobado, rechazado, etc.).
     */
    public Mono<Void> notificarCambioEstadoVendedor(
            String solicitudId,
            String telefonoVendedor, String nombreVendedor,
            String nombreCliente, String codigoDeSolicitud,
            String nuevoEstado, String motivo) {

        if (telefonoVendedor == null || telefonoVendedor.isBlank()) return Mono.empty();

        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("vendedor",          nombreVendedor     != null ? nombreVendedor     : "");
        vars.put("cliente",           nombreCliente      != null ? nombreCliente      : "");
        vars.put("codigoDeSolicitud", codigoDeSolicitud  != null ? codigoDeSolicitud  : solicitudId);
        vars.put("estado",            nuevoEstado        != null ? nuevoEstado        : "");
        vars.put("motivo",            motivo             != null ? motivo             : "");

        return publishEvent.publish(
                        BusinessEventType.SOLICITUD_INGRESADA, solicitudId,
                        NotificationChannel.WHATSAPP, telefonoVendedor,
                        NotificationTemplate.SOLICITUD_CAMBIO_ESTADO_VENDEDOR, vars)
                .onErrorResume(e -> { log.warn("[NotifFacade] WA vendedor estado error: {}", e.getMessage()); return Mono.empty(); });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String formatMonto(java.math.BigDecimal monto) {
        if (monto == null) return "S/ 0.00";
        return "S/ " + monto.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Devuelve true solo si el email tiene formato mínimamente válido (contiene @ y dominio). */
    private boolean esEmailValido(String email) {
        return email != null && !email.isBlank()
                && email.contains("@")
                && email.indexOf("@") < email.lastIndexOf(".");
    }
}
