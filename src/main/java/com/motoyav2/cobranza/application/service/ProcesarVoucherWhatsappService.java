package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ProcesarVoucherWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.RecibirVoucherUseCase;
import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
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
 * Orquesta el flujo completo cuando un cliente envía un comprobante de pago
 * a través del chat de WhatsApp:
 *
 *   1. Descarga la imagen desde Factiliza → sube a GCS
 *   2. En paralelo: extrae datos (Document AI + Claude) y carga el caso
 *      (para calcular montoEsperado y tener storeId)
 *   3. Registra el Voucher en cobranzas con estado PENDIENTE + datos OCR
 *   4. Confirma recepción al cliente por WhatsApp
 *
 * La decisión final de aprobar/rechazar queda en manos del revisor humano,
 * que verifica que el dinero ingresó en las cuentas de Motoya Digital.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesarVoucherWhatsappService implements ProcesarVoucherWhatsappUseCase {

    private final WhatsAppMediaStorageService mediaStorageService;
    private final ExtraerVoucherUseCase       extraerVoucher;
    private final RecibirVoucherUseCase       recibirVoucher;
    private final CasoCobranzaPort            casoPort;
    private final NotificationFacade          notificationFacade;

    @Override
    public Mono<Void> procesar(String contratoId, String storeId,
                               String clienteNombre, String clienteTelefono,
                               String mediaUrl, String mediaType) {

        log.info("[VOUCHER-WA] Procesando comprobante | contratoId={} phone={} tipo={}",
                contratoId, clienteTelefono, mediaType);

        return mediaStorageService.subirDesdeUrl(mediaUrl, mediaType, contratoId)
                .flatMap(upload -> {
                    String mimeType = "image".equals(mediaType) ? "image/jpeg" : "application/pdf";

                    Mono<VoucherExtraccion> extraccionMono =
                            extraerVoucher.extraer(upload.gcsUri(), mimeType);

                    // Cargar caso para montoEsperado (la cuota siguiente sin pagar)
                    Mono<CasoCobranzaDocument> casoMono =
                            casoPort.findById(contratoId)
                                    .defaultIfEmpty(new CasoCobranzaDocument());

                    return Mono.zip(extraccionMono, casoMono)
                            .flatMap(tuple -> {
                                VoucherExtraccion extraccion  = tuple.getT1();
                                CasoCobranzaDocument caso = tuple.getT2();

                                Double montoDetectado = parseMonto(extraccion.campos());
                                Double montoEsperado  = CuotaAplicador.montoProximaCuota(caso.getCronograma());
                                OcrResultadoDocument ocr = buildOcr(extraccion, montoDetectado);

                                log.info("[VOUCHER-WA] Extracción | contratoId={} banco={} monto={} esperado={} llm={}",
                                        contratoId, extraccion.banco(), montoDetectado,
                                        montoEsperado, extraccion.enriquecidoConLlm());

                                RecibirVoucherCommand command = new RecibirVoucherCommand(
                                        contratoId,
                                        storeId,
                                        upload.gcsPath(),
                                        null,
                                        montoDetectado,
                                        montoEsperado,
                                        ocr,
                                        "WHATSAPP_BOT"
                                );

                                return recibirVoucher.ejecutar(command)
                                        .flatMap(voucherId -> {
                                            log.info("[VOUCHER-WA] Voucher registrado | voucherId={} contratoId={}",
                                                    voucherId, contratoId);
                                            String banco   = extraccion.banco() != null
                                                    ? extraccion.banco() : "No identificado";
                                            String montoFmt = montoDetectado != null
                                                    ? String.format("S/ %.2f", montoDetectado)
                                                    : "Por determinar";
                                            return notificationFacade.notificarVoucherRecibidoCobranza(
                                                    contratoId, clienteTelefono,
                                                    clienteNombre != null ? clienteNombre : "Cliente",
                                                    banco, montoFmt);
                                        });
                            });
                })
                .doOnError(e -> log.error("[VOUCHER-WA] Error | contratoId={} error={}",
                        contratoId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
