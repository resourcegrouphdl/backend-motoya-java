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

                    String mimeType = "image".equals(mediaType) ? "image/jpeg" : "application/pdf";
                    Mono<VoucherExtraccion> extraccionMono = extraerVoucher.extraer(upload.gcsUri(), mimeType);
                    Mono<CasoCobranzaDocument> casoMono = casoPort.findById(contratoId)
                            .defaultIfEmpty(new CasoCobranzaDocument());

                    return Mono.zip(extraccionMono, casoMono)
                            .flatMap(tuple -> {
                                VoucherExtraccion extraccion = tuple.getT1();
                                CasoCobranzaDocument caso    = tuple.getT2();

                                Double montoDetectado = parseMonto(extraccion.campos());

                                // Umbral de confianza: necesitamos al menos monto O banco identificado
                                boolean esVoucher = montoDetectado != null || extraccion.banco() != null;
                                if (!esVoucher) {
                                    log.info("[VOUCHER-WA] Imagen descartada — sin monto ni banco | contratoId={} mensajeId={}",
                                            contratoId, mensajeId);
                                    return marcarNoVoucher(mensajeId);
                                }

                                Double montoEsperado = CuotaAplicador.montoProximaCuota(caso.getCronograma());
                                OcrResultadoDocument ocr = buildOcr(extraccion, montoDetectado);

                                log.info("[VOUCHER-WA] Voucher detectado | contratoId={} banco={} monto={} esperado={} llm={}",
                                        contratoId, extraccion.banco(), montoDetectado,
                                        montoEsperado, extraccion.enriquecidoConLlm());

                                RecibirVoucherCommand command = new RecibirVoucherCommand(
                                        contratoId, storeId, upload.gcsPath(), null,
                                        montoDetectado, montoEsperado, ocr,
                                        "WHATSAPP_BOT", "WHATSAPP", clienteNombre
                                );

                                return recibirVoucher.ejecutar(command)
                                        .flatMap(voucherId -> {
                                            log.info("[VOUCHER-WA] Voucher registrado | voucherId={} contratoId={}", voucherId, contratoId);
                                            marcarComoVoucher(mensajeId, voucherId)
                                                    .subscribe(null, e -> log.warn("[VOUCHER-WA] Error marcando voucher en mensaje: {}", e.getMessage()));
                                            String banco    = extraccion.banco() != null ? extraccion.banco() : "No identificado";
                                            String montoFmt = montoDetectado != null
                                                    ? String.format("S/ %.2f", montoDetectado) : "Por determinar";
                                            return notificationFacade.notificarVoucherRecibidoCobranza(
                                                    contratoId, clienteTelefono,
                                                    clienteNombre != null ? clienteNombre : "Cliente",
                                                    banco, montoFmt);
                                        });
                            });
                })
                .doOnError(e -> log.error("[VOUCHER-WA] Error | contratoId={} error={}", contratoId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
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

    private Double parseMonto(Map<String, String> campos) {
        if (campos == null) return null;
        String raw = campos.get("montoPagado");
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            log.warn("[VOUCHER-WA] No se pudo parsear monto: {}", raw);
            return null;
        }
    }
}
