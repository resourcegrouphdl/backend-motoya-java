package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.out.SignedUrlStoragePort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Endpoint para generar Signed URLs de GCS.
 *
 * Flujo de subida de archivos:
 *   1. Frontend llama POST /api/finanzas/storage/signed-upload-url
 *   2. Backend devuelve {signedUploadUrl, publicUrl, gcsPath, expiraEn}
 *   3. Frontend hace PUT a signedUploadUrl con el archivo binario
 *      (Content-Type debe coincidir con el solicitado)
 *   4. Frontend usa publicUrl como valor del campo voucherUrl en los endpoints de pago
 */
@RestController
@RequestMapping("/api/finanzas/storage")
@RequiredArgsConstructor
public class StorageController {

    private final SignedUrlStoragePort signedUrlStoragePort;

    private static final java.util.Set<String> MIME_PERMITIDOS = java.util.Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp",
            "image/tiff", "application/pdf"
    );

    @PostMapping("/signed-upload-url")
    public Mono<SignedUrlResponse> generarUrlCarga(
            @Valid @RequestBody SignedUrlRequest request) {

        if (!MIME_PERMITIDOS.contains(request.contentType().toLowerCase())) {
            return Mono.error(new com.motoyav2.shared.exception.BadRequestException(
                    "Formato no permitido: " + request.contentType()
                    + ". Use: JPG, PNG, WEBP, TIFF o PDF."));
        }

        String gcsPath = buildGcsPath(request);

        return signedUrlStoragePort.generarUrlCarga(gcsPath, request.contentType())
                .map(result -> new SignedUrlResponse(
                        result.signedUploadUrl(),
                        result.publicUrl(),
                        result.gcsPath(),
                        Instant.now().plusSeconds(900).toString() // 15 min
                ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Construye la ruta GCS según el contexto:
     *   pago-factura  → finanzas/vouchers/{referenceId1}/{referenceId2}_{ts}.{ext}
     *   cuota-cuenta  → finanzas/comprobantes/{referenceId1}/{referenceId2}_{ts}.{ext}
     */
    private String buildGcsPath(SignedUrlRequest req) {
        String ext = extensionDe(req.contentType());
        String ts  = String.valueOf(Instant.now().getEpochSecond());
        String base = req.referenceId2() != null
                ? req.referenceId1() + "/" + req.referenceId2() + "_" + ts + "." + ext
                : req.referenceId1() + "/" + ts + "." + ext;
        return switch (req.contexto()) {
            case "cuota-cuenta"    -> "finanzas/comprobantes/" + base;
            case "voucher-comision"-> "finanzas/vouchers-comisiones/" + base;
            default                -> "finanzas/vouchers/" + base;   // pago-factura
        };
    }

    private String extensionDe(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/png"       -> "png";
            case "image/webp"      -> "webp";
            case "image/tiff"      -> "tiff";
            default                -> "jpg";
        };
    }

    // ── DTOs inline ───────────────────────────────────────────────────────────

    public record SignedUrlRequest(
            /** pago-factura | cuota-cuenta */
            @NotBlank String contexto,
            /** facturaId o cuentaId */
            @NotBlank String referenceId1,
            /** pagoId o cuotaId (opcional) */
            String referenceId2,
            /** MIME type del archivo que se va a subir */
            @NotBlank String contentType
    ) {}

    public record SignedUrlResponse(
            /** URL para PUT directo al bucket GCS (expira en 15 min) */
            String signedUploadUrl,
            /** URL pública permanente — guardar este valor en el pago */
            String publicUrl,
            /** Ruta interna en GCS */
            String gcsPath,
            /** ISO timestamp de expiración de la signed URL */
            String expiraEn
    ) {}
}
