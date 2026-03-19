package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.out.DocumentAiPort;
import com.motoyav2.finanzas.application.port.out.FacturaPort;
import com.motoyav2.finanzas.application.port.out.CuentaPorPagarPort;
import com.motoyav2.finanzas.domain.model.DocumentAiExtraccion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Orquesta la extracción asíncrona de campos de documentos financieros
 * usando Google Document AI y persiste el resultado en Firestore.
 *
 * Se dispara como fire-and-forget: NO bloquea la respuesta HTTP al cliente.
 * El estado de la extracción queda en el campo documentAiStatus del pago/cuota.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAiService {

    private final DocumentAiPort documentAiPort;
    private final FacturaPort facturaPort;
    private final CuentaPorPagarPort cuentaPort;

    /**
     * Dispara la extracción de un voucher de pago de factura.
     * Se suscribe inmediatamente en un scheduler separado y retorna Mono.empty()
     * para no bloquear el flujo principal.
     */
    public void extraerAsync(String facturaId, String pagoId, String gcsPath, String mimeType) {
        String mime = resolverMimeType(gcsPath, mimeType);
        log.info("[DocumentAI] Iniciando extracción — facturaId={} pagoId={} gcsPath={} mime={}", facturaId, pagoId, gcsPath, mime);
        documentAiPort.procesar(gcsPath, mime)
                .doOnNext(e -> log.info("[DocumentAI] Extracción completada — facturaId={} pagoId={} status={} campos={}",
                        facturaId, pagoId, e.status(), e.campos() != null ? e.campos().size() : 0))
                .flatMap(extraccion -> persistirEnPago(facturaId, pagoId, extraccion))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v  -> log.info("[DocumentAI] Persistido OK — facturaId={} pagoId={}", facturaId, pagoId),
                        ex -> {
                            log.error("[DocumentAI] Error en extracción — facturaId={} pagoId={}: {}", facturaId, pagoId, ex.getMessage(), ex);
                            // Actualizar Firestore con ERROR para que el frontend no quede colgado
                            persistirStatusError(facturaId, pagoId, ex.getMessage(), true)
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe(
                                            v2 -> log.info("[DocumentAI] Status ERROR persistido — facturaId={} pagoId={}", facturaId, pagoId),
                                            e2 -> log.error("[DocumentAI] No se pudo persistir ERROR — facturaId={} pagoId={}: {}", facturaId, pagoId, e2.getMessage())
                                    );
                        }
                );
    }

    /**
     * Dispara la extracción de un comprobante de cuota de cuenta por pagar.
     */
    public void extraerAsyncCuota(String cuentaId, String cuotaId, String gcsPath, String mimeType) {
        String mime = resolverMimeType(gcsPath, mimeType);
        log.info("[DocumentAI] Iniciando extracción cuota — cuentaId={} cuotaId={} gcsPath={} mime={}", cuentaId, cuotaId, gcsPath, mime);
        documentAiPort.procesar(gcsPath, mime)
                .doOnNext(e -> log.info("[DocumentAI] Extracción completada — cuentaId={} cuotaId={} status={} campos={}",
                        cuentaId, cuotaId, e.status(), e.campos() != null ? e.campos().size() : 0))
                .flatMap(extraccion -> persistirEnCuota(cuentaId, cuotaId, extraccion))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v  -> log.info("[DocumentAI] Persistido OK — cuentaId={} cuotaId={}", cuentaId, cuotaId),
                        ex -> {
                            log.error("[DocumentAI] Error en extracción — cuentaId={} cuotaId={}: {}", cuentaId, cuotaId, ex.getMessage(), ex);
                            // Actualizar Firestore con ERROR para que el frontend no quede colgado
                            persistirStatusError(cuentaId, cuotaId, ex.getMessage(), false)
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe(
                                            v2 -> log.info("[DocumentAI] Status ERROR persistido — cuentaId={} cuotaId={}", cuentaId, cuotaId),
                                            e2 -> log.error("[DocumentAI] No se pudo persistir ERROR — cuentaId={} cuotaId={}: {}", cuentaId, cuotaId, e2.getMessage())
                                    );
                        }
                );
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    private Mono<Void> persistirEnPago(String facturaId, String pagoId, DocumentAiExtraccion extraccion) {
        Map<String, Object> campos = Map.of(
                "documentAiStatus", extraccion.status(),
                "documentAiCampos", extraccion.campos() != null ? extraccion.campos() : Map.of(),
                "documentAiProcesadoEn", extraccion.procesadoEn() != null ? extraccion.procesadoEn() : ""
        );
        return facturaPort.actualizarDocumentAi(facturaId, pagoId, campos)
                .doOnError(e -> log.error("[DocumentAI] Fallo persistiendo en Firestore (pago) — facturaId={} pagoId={}: {}", facturaId, pagoId, e.getMessage()));
    }

    private Mono<Void> persistirEnCuota(String cuentaId, String cuotaId, DocumentAiExtraccion extraccion) {
        Map<String, Object> campos = Map.of(
                "documentAiStatus", extraccion.status(),
                "documentAiCampos", extraccion.campos() != null ? extraccion.campos() : Map.of(),
                "documentAiProcesadoEn", extraccion.procesadoEn() != null ? extraccion.procesadoEn() : ""
        );
        return cuentaPort.actualizarCuota(cuentaId, cuotaId, campos)
                .doOnError(e -> log.error("[DocumentAI] Fallo persistiendo en Firestore (cuota) — cuentaId={} cuotaId={}: {}", cuentaId, cuotaId, e.getMessage()));
    }

    /** Persiste status=ERROR en Firestore cuando la cadena reactiva falla inesperadamente. */
    private Mono<Void> persistirStatusError(String id1, String id2, String errorMsg, boolean esPago) {
        java.time.Instant ahora = java.time.Instant.now();
        Map<String, Object> campos = Map.of(
                "documentAiStatus", "ERROR",
                "documentAiCampos", Map.of(),
                "documentAiProcesadoEn", ahora.toString(),
                "documentAiError", errorMsg != null ? errorMsg : "Error desconocido"
        );
        return esPago
                ? facturaPort.actualizarDocumentAi(id1, id2, campos)
                : cuentaPort.actualizarCuota(id1, id2, campos);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Si el mimeType viene vacío, lo infiere por extensión del path.
     * Document AI acepta: image/jpeg, image/png, image/webp, image/tiff, application/pdf
     */
    private String resolverMimeType(String gcsPath, String mimeType) {
        if (mimeType != null && !mimeType.isBlank()) return mimeType;
        String lower = gcsPath.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        return "image/jpeg"; // default
    }
}
