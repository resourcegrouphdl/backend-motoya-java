package com.motoyav2.whatsapp.infrastructure.security;

import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookSignatureFilterTest {

    private static final String APP_SECRET = "test-secret-12345";
    private static final String BODY       = "{\"object\":\"whatsapp_business_account\"}";

    private WebhookSignatureFilter filter;

    @BeforeEach
    void setUp() {
        MetaWhatsAppProperties props = new MetaWhatsAppProperties();
        props.setAppSecret(APP_SECRET);
        filter = new WebhookSignatureFilter(props);
    }

    @Test
    void requestConFirmaValidaPasaAlChain() throws Exception {
        String signature = "sha256=" + computeHmac(BODY, APP_SECRET);
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/webhook/meta")
            .header("X-Hub-Signature-256", signature)
            .body(BODY);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();
    }

    @Test
    void requestSinHeaderDevuelve401() {
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/webhook/meta")
            .body(BODY);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestConFirmaIncorrectaDevuelve401() {
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/webhook/meta")
            .header("X-Hub-Signature-256", "sha256=badbadbad")
            .body(BODY);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void otrasRutasNoFiltradas() {
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/api/v1/cobranzas/casos")
            .body(BODY);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();
        // No debe rechazar — sin body válido en este path el chain pasa igual
    }

    @Test
    void getMetaNoFiltrado() {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/webhook/meta?hub.mode=subscribe")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String computeHmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
