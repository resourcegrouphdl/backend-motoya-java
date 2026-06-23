package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ProcesarVoucherWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.RecibirVoucherUseCase;
import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.OcrResultadoDocument;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import com.motoyav2.voucherextraction.application.port.in.ExtraerVoucherUseCase;
import com.motoyav2.voucherextraction.domain.model.VoucherExtraccion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Orquesta el flujo cuando un cliente envía una imagen por WhatsApp:
 *
 *   1. Sube imagen a GCS — actualiza MensajeWhatsapp con gcsMediaUrl
 *   2. Extrae datos con Document AI + Claude y carga el caso en paralelo
 *   3. Umbral de confianza: si no hay monto NI banco detectados → descarta como
 *      voucher (esVoucher=false) pero la imagen sigue visible en el chat
 *   4. Si supera el umbral → registra Voucher PENDIENTE + notifica al cliente
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesarVoucherWhatsappService implements ProcesarVoucherWhatsappUseCase {

    private final WhatsAppMediaStorageService mediaStorageService;
    private final ExtraerVoucherUseCase       extraerVoucher;
    private final RecibirVoucherUseCase       recibirVoucher;
    private final CasoCobranzaPort            casoPort;
    private final MensajeWhatsappPort         mensajePort;
    private final NotificationFacade          notificationFacade;

    @Value("${app.notifications.cobranzas.enabled:false}")
    private boolean notificacionesEnabled;

    @Override
    public Mono<Void> procesar(String contratoId, String storeId,
                               String clienteNombre, String clienteTelefono,
                               String mediaUrl, String mediaType, String mensajeId) {

        log.info("[VOUCHER-WA] Procesando imagen | contratoId={} phone={} tipo={}",
                contratoId, clienteTelefono, mediaType);

        return mediaStorageService.subirDesdeUrl(mediaUrl, mediaType, contratoId)
                .flatMap(upload -> {
                    // Guardamos la URL de GCS en el mensaje de forma asíncrona (no bloquea el flujo)
                    actualizarGcsUrl(mensajeId, upload.gcsPath())
                            .subscribe(null, e -> log.warn("[VOUCHER-WA] Error actualizando gcsMediaUrl mensajeId={}: {}", mensajeId, e.getMessage()));

                    // mimeType para Document AI: derivado del mediaType genérico recibido del dispatcher.
                    // El mimeType real (image/png, image/jpeg) llega via WA-3 cuando el webhook lo incluye.
                    String mimeType = switch (mediaType) {
                        case "image"    -> "image/jpeg";
                        case "document" -> "application/pdf";
                        default         -> "image/jpeg";
                    };
                    Mono<VoucherExtraccion> extraccionMono = extraerVoucher.extraer(upload.gcsUri(), mimeType);
                    Mono<CasoCobranzaDocument> casoMono = casoPort.findById(contratoId)
                            .defaultIfEmpty(new CasoCobranzaDocument());

                    return Mono.zip(extraccionMono, casoMono)
                            .flatMap(tuple -> {
                                VoucherExtraccion extraccion = tuple.getT1();
                                CasoCobranzaDocument caso    = tuple.getT2();

                                Double montoDetectado = parseMonto(extraccion.campos());

                                // Umbral de confianza (4 niveles):
                                //   1. Banco específico reconocido (no GENERICO/DESCONOCIDO)
                                //   2. Monto detectado
                                //   3. Fallback: Document AI extrajo algún campo → imagen con texto, probable recibo
                                //   4. PDF: siempre se registra para revisión manual del agente
                                //      (el OCR falla en PDFs escaneados pero el agente puede aprobarlo/rechazarlo)
                                boolean bancoCierto = extraccion.banco() != null
                                        && !"GENERICO".equals(extraccion.banco())
                                        && !"DESCONOCIDO".equals(extraccion.banco());
                                boolean tieneCampos = extraccion.campos() != null && !extraccion.campos().isEmpty();
                                boolean esPdf = "document".equals(mediaType);
                                boolean esVoucher = bancoCierto || montoDetectado != null || tieneCampos || esPdf;
                                if (!esVoucher) {
                                    log.info("[VOUCHER-WA] Imagen descartada — OCR sin texto reconocible | "
                                                    + "contratoId={} mensajeId={} banco={} campos={}",
                                            contratoId, mensajeId,
                                            extraccion.banco(),
                                            extraccion.campos() != null ? extraccion.campos().keySet() : "null");
                                    return marcarNoVoucher(mensajeId);
                                }

                                Double montoEsperado = CuotaAplicador.montoProximaCuota(caso.getCronograma());
                                OcrResultadoDocument ocr = buildOcr(extraccion, montoDetectado);

                                // Resolver storeId desde el caso; migrados sin tienda → COBRANZAS
                                String effectiveStoreId = (storeId != null && !storeId.isBlank())
                                        ? storeId
                                        : (caso.getStoreId() != null && !caso.getStoreId().isBlank())
                                                ? caso.getStoreId()
                                                : IniciarCasoService.STORE_COBRANZAS;
                                String effectiveCliente = (clienteNombre != null && !clienteNombre.isBlank())
                                        ? clienteNombre : caso.getClienteNombre();

                                log.info("[VOUCHER-WA] Voucher detectado | contratoId={} banco={} monto={} esperado={} storeId={} llm={}",
                                        contratoId, extraccion.banco(), montoDetectado,
                                        montoEsperado, effectiveStoreId, extraccion.enriquecidoConLlm());

                                RecibirVoucherCommand command = new RecibirVoucherCommand(
                                        contratoId, effectiveStoreId, upload.gcsPath(), null,
                                        montoDetectado, montoEsperado, ocr,
                                        "WHATSAPP_BOT", "WHATSAPP", effectiveCliente,
                                        mediaType
                                );

                                return recibirVoucher.ejecutar(command)
                                        .flatMap(voucherId -> {
                                            log.info("[VOUCHER-WA] Voucher registrado | voucherId={} contratoId={}", voucherId, contratoId);
                                            marcarComoVoucher(mensajeId, voucherId)
                                                    .subscribe(null, e -> log.warn("[VOUCHER-WA] Error marcando voucher en mensaje: {}", e.getMessage()));
                                            String banco = (extraccion.banco() != null
                                                    && !"GENERICO".equals(extraccion.banco())
                                                    && !"DESCONOCIDO".equals(extraccion.banco()))
                                                    ? extraccion.banco() : "No identificado";
                                            String montoFmt = montoDetectado != null
                                                    ? String.format("S/ %.2f", montoDetectado) : "Por determinar";
                                            if (!notificacionesEnabled) {
                                                log.info("[VOUCHER-WA] Notificación WA desactivada (app.notifications.cobranzas.enabled=false) — contratoId={}", contratoId);
                                                return Mono.empty();
                                            }
                                            return notificationFacade.notificarVoucherRecibidoCobranza(
                                                    contratoId, clienteTelefono,
                                                    clienteNombre != null ? clienteNombre : "Cliente",
                                                    banco, montoFmt);
                                        });
                            });
                })
                .doOnError(e -> log.error("[VOUCHER-WA] Error en pipeline | contratoId={} mensajeId={} error={}",
                        contratoId, mensajeId, e.getMessage()))
                .onErrorResume(e -> {
                    // Marcar el mensaje con error de procesamiento para que el agente
                    // vea "Error al procesar" en lugar de "procesando..." indefinidamente.
                    if (mensajeId != null) {
                        mensajePort.findById(mensajeId)
                                .flatMap(msg -> {
                                    msg.setEsVoucher(false);
                                    msg.setErrorProcesamiento(
                                            "Error al procesar imagen: " + e.getClass().getSimpleName());
                                    return mensajePort.save(msg);
                                })
                                .subscribe(null,
                                        saveErr -> log.warn("[VOUCHER-WA] No se pudo marcar error en mensaje: {}", saveErr.getMessage()));
                    }
                    return Mono.empty();
                });
    }

    // ── Helpers de actualización de mensaje ───────────────────────────────────

    private Mono<Void> actualizarGcsUrl(String mensajeId, String gcsPath) {
        if (mensajeId == null) return Mono.empty();
        return mensajePort.findById(mensajeId)
                .flatMap(msg -> {
                    msg.setGcsMediaUrl(gcsPath);
                    return mensajePort.save(msg);
                })
                .then();
    }

    private Mono<Void> marcarNoVoucher(String mensajeId) {
        if (mensajeId == null) return Mono.empty();
        return mensajePort.findById(mensajeId)
                .flatMap(msg -> {
                    msg.setEsVoucher(false);
                    return mensajePort.save(msg);
                })
                .then();
    }

    private Mono<Void> marcarComoVoucher(String mensajeId, String voucherId) {
        if (mensajeId == null) return Mono.empty();
        return mensajePort.findById(mensajeId)
                .flatMap(msg -> {
                    msg.setEsVoucher(true);
                    msg.setVoucherId(voucherId);
                    return mensajePort.save(msg);
                })
                .then();
    }

    // ── Helpers OCR ───────────────────────────────────────────────────────────

    private OcrResultadoDocument buildOcr(VoucherExtraccion extraccion, Double monto) {
        if (extraccion == null) return null;
        Map<String, String> campos = extraccion.campos();
        String procesador = extraccion.enriquecidoConLlm() ? "DOCUMENT_AI_LLM" : "DOCUMENT_AI";
        double confianza  = extraccion.enriquecidoConLlm() ? 0.85 : 0.75;
        return OcrResultadoDocument.builder()
                .banco(extraccion.banco())
                .numeroOperacion(campos != null ? campos.get("numeroOperacion") : null)
                .fecha(campos != null ? campos.get("fechaPago") : null)
                .monto(monto)
                .confianza(confianza)
                .procesador(procesador)
                .build();
    }

    private static final String[] MONTO_KEYS = {
            "montoPagado", "monto", "importe", "total", "amount", "montoTotal", "montoOperacion"
    };

    private Double parseMonto(Map<String, String> campos) {
        if (campos == null) return null;
        String raw = null;
        for (String key : MONTO_KEYS) {
            String val = campos.get(key);
            if (val != null && !val.isBlank()) { raw = val; break; }
        }
        if (raw == null) return null;
        try {
            String numeric = normalizarNumero(raw);
            return numeric.isBlank() ? null : Double.parseDouble(numeric);
        } catch (NumberFormatException e) {
            log.warn("[VOUCHER-WA] No se pudo parsear monto: {}", raw);
            return null;
        }
    }

    private String normalizarNumero(String raw) {
        // Quitar símbolo de moneda y espacios: "S/ 1,200.50" → "1,200.50"
        String s = raw.replaceAll("[^0-9.,]", "");
        // Formato europeo "1.200,50" → "1200.50"
        if (s.matches("\\d{1,3}(\\.\\d{3})*(,\\d{1,2})?")) {
            return s.replace(".", "").replace(",", ".");
        }
        // Formato estándar "1,200.50" → "1200.50"
        return s.replace(",", "");
    }
}
