package com.motoyav2.whatsapp.infrastructure.security;

import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Valida la firma HMAC-SHA256 del webhook de Meta Cloud API.
 *
 * Activar con: notifications.webhook.signature-validation.enabled=true
 * Requiere configurar notifications.meta.app-secret en Cloud Run env vars.
 *
 * Meta envía: X-Hub-Signature-256: sha256=<hmac-hex>
 * donde el HMAC usa el App Secret (no el access token) como clave.
 */
@Slf4j
@Component
@Order(-100)
@ConditionalOnProperty(name = "notifications.webhook.signature-validation.enabled", havingValue = "true")
@RequiredArgsConstructor
public class WebhookSignatureFilter implements WebFilter {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String META_WEBHOOK_PATH = "/webhook/meta";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final MetaWhatsAppProperties metaProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!META_WEBHOOK_PATH.equals(request.getPath().value())
                || !"POST".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        String signatureHeader = request.getHeaders().getFirst(SIGNATURE_HEADER);
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("[SIGNATURE-FILTER] Header {} ausente o mal formado — request rechazado", SIGNATURE_HEADER);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String expectedHex = signatureHeader.substring("sha256=".length());

        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    String actualHex = computeHmac(bodyBytes, metaProperties.getAppSecret());
                    if (!safeEquals(expectedHex, actualHex)) {
                        log.warn("[SIGNATURE-FILTER] Firma inválida — request rechazado");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    // Reconstruir request con el body ya leído para que el controller pueda leerlo de nuevo
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bodyBytes);
                            return Flux.just(buffer);
                        }
                    };
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(e -> {
                    log.error("[SIGNATURE-FILTER] Error validando firma: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return exchange.getResponse().setComplete();
                });
    }

    private String computeHmac(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[SIGNATURE-FILTER] Error calculando HMAC: {}", e.getMessage());
            return "";
        }
    }

    /** Comparación en tiempo constante para evitar timing attacks. */
    private boolean safeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
