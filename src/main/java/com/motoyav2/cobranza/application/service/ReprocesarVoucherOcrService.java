package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ReprocesarVoucherOcrUseCase;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.OcrResultadoDocument;
import com.motoyav2.voucherextraction.application.port.in.ExtraerVoucherUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReprocesarVoucherOcrService implements ReprocesarVoucherOcrUseCase {

    private final VoucherPort voucherPort;
    private final ExtraerVoucherUseCase extraerVoucher;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Override
    public Mono<ReprocesarOcrResult> ejecutar(String voucherId) {
        log.info("[REPROCESAR-OCR] Iniciando | voucherId={}", voucherId);

        return voucherPort.findById(voucherId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Voucher no encontrado: " + voucherId)))
                .flatMap(voucher -> {
                    String imagenPath = voucher.getImagenPath();
                    if (imagenPath == null || imagenPath.isBlank()) {
                        return Mono.error(new IllegalStateException("El voucher no tiene imagen en GCS"));
                    }

                    // Construimos gs:// URI a partir del path relativo
                    String gcsUri  = imagenPath.startsWith("gs://") ? imagenPath
                            : "gs://" + bucketName + "/" + imagenPath;
                    String mimeType = imagenPath.toLowerCase().endsWith(".pdf")
                            ? "application/pdf" : "image/jpeg";

                    Double montoAnterior = voucher.getMontoDetectado();

                    return extraerVoucher.extraer(gcsUri, mimeType)
                            .flatMap(extraccion -> {
                                Double montoNuevo = parseMonto(extraccion.campos());
                                boolean cambiado  = !Objects.equals(montoAnterior, montoNuevo);
                                String procesador = extraccion.enriquecidoConLlm()
                                        ? "DOCUMENT_AI_LLM" : "DOCUMENT_AI";

                                log.info("[REPROCESAR-OCR] Resultado | voucherId={} banco={} montoAntes={} montoNuevo={} cambiado={} llm={}",
                                        voucherId, extraccion.banco(), montoAnterior,
                                        montoNuevo, cambiado, extraccion.enriquecidoConLlm());

                                OcrResultadoDocument ocrActualizado = OcrResultadoDocument.builder()
                                        .banco(extraccion.banco())
                                        .numeroOperacion(campo(extraccion.campos(), "numeroOperacion"))
                                        .fecha(campo(extraccion.campos(), "fechaPago"))
                                        .monto(montoNuevo)
                                        .confianza(extraccion.enriquecidoConLlm() ? 0.85 : 0.75)
                                        .procesador(procesador)
                                        .build();

                                voucher.setMontoDetectado(montoNuevo);
                                voucher.setOcrResultado(ocrActualizado);
                                voucher.setActualizadoEn(new Date());

                                return voucherPort.save(voucher)
                                        .thenReturn(new ReprocesarOcrResult(
                                                voucherId,
                                                montoAnterior,
                                                montoNuevo,
                                                extraccion.banco(),
                                                extraccion.campos(),
                                                extraccion.enriquecidoConLlm(),
                                                cambiado,
                                                procesador
                                        ));
                            });
                })
                .doOnError(e -> log.error("[REPROCESAR-OCR] Error | voucherId={} error={}", voucherId, e.getMessage()));
    }

    private String campo(Map<String, String> campos, String key) {
        return campos != null ? campos.get(key) : null;
    }

    private Double parseMonto(Map<String, String> campos) {
        if (campos == null) return null;
        String raw = campos.get("montoPagado");
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            log.warn("[REPROCESAR-OCR] No se pudo parsear monto: {}", raw);
            return null;
        }
    }
}
