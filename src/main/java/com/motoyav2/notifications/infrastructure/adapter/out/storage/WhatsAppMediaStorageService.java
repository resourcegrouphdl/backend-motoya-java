package com.motoyav2.notifications.infrastructure.adapter.out.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.FactilizaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sube archivos recibidos por WhatsApp (base64) a Firebase Storage.
 * Ruta: whatsapp-media/{solicitudId-o-uuid}/{filename}
 * Retorna Signed URL válida 7 días.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMediaStorageService {

    private static final Map<String, String> MIME_MAP = Map.of(
            "image",    "image/jpeg",
            "document", "application/pdf",
            "audio",    "audio/ogg",
            "video",    "video/mp4",
            "sticker",  "image/webp"
    );

    private final Storage storage;
    private final FactilizaProperties factilizaProperties;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.gcs.bucket-name:motoya-form.appspot.com}")
    private String bucketName;

    /** Resultado de una subida: gcsPath relativo + URI para Document AI. */
    public record MediaUploadResult(String gcsPath, String gcsUri) {}

    /**
     * Decodifica base64 y sube a GCS.
     *
     * @param base64Data  Contenido en base64 (con o sin prefijo data:...)
     * @param mediaType   Tipo: "image", "document", "audio", etc.
     * @param filename    Nombre de archivo sugerido (puede ser null)
     * @param contextId   ID de solicitud u otro contexto (para la ruta en GCS)
     * @return Signed URL pública del archivo subido
     */
    public Mono<String> subirBase64(String base64Data, String mediaType, String filename, String contextId) {
        return Mono.fromCallable(() -> {
            // Limpiar prefijo "data:image/jpeg;base64," si viene
            String cleanBase64 = base64Data.replaceAll("^data:[^;]+;base64,", "").trim();
            byte[] bytes = Base64.getDecoder().decode(cleanBase64);

            String ext      = resolveExtension(mediaType, filename);
            String safeName = filename != null ? sanitize(filename) : (UUID.randomUUID() + "." + ext);
            String folder   = contextId != null ? contextId : UUID.randomUUID().toString();
            String gcsPath  = "whatsapp-media/" + folder + "/" + safeName;

            String mime = MIME_MAP.getOrDefault(mediaType != null ? mediaType.toLowerCase() : "", "application/octet-stream");

            BlobId   blobId   = BlobId.of(bucketName, gcsPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(mime).build();
            storage.create(blobInfo, bytes);

            URL signedUrl = storage.signUrl(blobInfo, 7, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature());
            log.info("[WA-MEDIA] Subido a GCS | path={} size={}KB", gcsPath, bytes.length / 1024);
            return signedUrl.toString();
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("[WA-MEDIA] Error subiendo media a GCS: {}", e.getMessage()));
    }

    private String resolveExtension(String mediaType, String filename) {
        if (filename != null && filename.contains(".")) return filename.substring(filename.lastIndexOf('.') + 1);
        if (mediaType == null) return "bin";
        return switch (mediaType.toLowerCase()) {
            case "image"    -> "jpg";
            case "document" -> "pdf";
            case "audio"    -> "ogg";
            case "video"    -> "mp4";
            default         -> "bin";
        };
    }

    /**
     * Sube bytes en memoria a GCS (para uploads multipart desde el controller).
     * Ruta: cobranzas-pagos-manuales/{contextId}/{filename}
     */
    public Mono<MediaUploadResult> subirBytes(byte[] bytes, String mediaType, String filename, String contextId) {
        return Mono.fromCallable(() -> {
            String ext      = resolveExtension(mediaType, filename);
            String safeName = filename != null ? sanitize(filename) : (UUID.randomUUID() + "." + ext);
            String folder   = contextId != null ? contextId : UUID.randomUUID().toString();
            String gcsPath  = "cobranzas-pagos-manuales/" + folder + "/" + safeName;
            String mime     = MIME_MAP.getOrDefault(
                    mediaType != null ? mediaType.toLowerCase() : "", "application/octet-stream");

            BlobId   blobId   = BlobId.of(bucketName, gcsPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(mime).build();
            storage.create(blobInfo, bytes);

            log.info("[WA-MEDIA] Subido a GCS | path={} size={}KB", gcsPath, bytes.length / 1024);
            return new MediaUploadResult(gcsPath, "gs://" + bucketName + "/" + gcsPath);
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("[WA-MEDIA] Error subiendo bytes a GCS: {}", e.getMessage()));
    }

    /**
     * Descarga bytes desde una URL de Factiliza y los sube a GCS.
     * Retorna gcsPath (relativo) y gcsUri (gs://bucket/path) para Document AI.
     *
     * @param mediaUrl   URL de descarga del media (de Factiliza webhook)
     * @param mediaType  Tipo: "image", "document", etc.
     * @param contextId  ID de contrato u otro contexto (para la ruta en GCS)
     */
    public Mono<MediaUploadResult> subirDesdeUrl(String mediaUrl, String mediaType, String contextId) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return Mono.error(new IllegalArgumentException("mediaUrl es nulo o vacío"));
        }

        WebClient client = webClientBuilder.build();

        return client.get()
                .uri(mediaUrl)
                .header("Authorization", "Bearer " + factilizaProperties.getToken())
                .retrieve()
                .bodyToMono(byte[].class)
                .flatMap(bytes -> Mono.fromCallable(() -> {
                    String ext      = resolveExtension(mediaType, null);
                    String filename = UUID.randomUUID() + "." + ext;
                    String folder   = contextId != null ? contextId : UUID.randomUUID().toString();
                    String gcsPath  = "cobranza-vouchers/" + folder + "/" + filename;
                    String mime     = MIME_MAP.getOrDefault(
                            mediaType != null ? mediaType.toLowerCase() : "", "application/octet-stream");

                    BlobId   blobId   = BlobId.of(bucketName, gcsPath);
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(mime).build();
                    storage.create(blobInfo, bytes);

                    log.info("[WA-MEDIA] Descargado y subido a GCS | path={} size={}KB",
                            gcsPath, bytes.length / 1024);
                    return new MediaUploadResult(gcsPath, "gs://" + bucketName + "/" + gcsPath);
                }).subscribeOn(Schedulers.boundedElastic()))
                .doOnError(e -> log.error("[WA-MEDIA] Error subiendo desde URL a GCS: {}", e.getMessage()));
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
