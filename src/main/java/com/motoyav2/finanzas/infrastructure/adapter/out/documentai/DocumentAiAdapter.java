package com.motoyav2.finanzas.infrastructure.adapter.out.documentai;

import com.google.auth.oauth2.GoogleCredentials;
import com.motoyav2.finanzas.application.port.out.DocumentAiPort;
import com.motoyav2.finanzas.domain.model.DocumentAiExtraccion;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador que llama a Google Document AI REST API para extraer campos
 * de vouchers, comprobantes y facturas subidos a GCS.
 *
 * Usa la REST API directamente (no el cliente Java) para mantener el modelo
 * reactivo sin wrapping de llamadas bloqueantes pesadas.
 *
 * Requiere:
 *   - app.documentai.project-id
 *   - app.documentai.location (us | eu)
 *   - app.documentai.processor-id  (creado en Google Cloud Console)
 *   - app.documentai.enabled=true
 *   - app.gcs.bucket-name
 *
 * El procesador recomendado es "Form Parser" que maneja tanto facturas
 * como vouchers de transferencia bancaria.
 */
@Slf4j
@Component
public class DocumentAiAdapter implements DocumentAiPort {

    private static final String DOCAI_BASE = "https://documentai.googleapis.com/v1";
    private static final String DOCAI_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final WebClient webClient;

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Value("${app.documentai.project-id:motoya-form}")
    private String projectId;

    @Value("${app.documentai.location:us}")
    private String location;

    @Value("${app.documentai.processor-id:}")
    private String processorId;

    @Value("${app.documentai.enabled:false}")
    private boolean enabled;

    public DocumentAiAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10 MB
                .baseUrl(DOCAI_BASE)
                .build();
    }

    @Override
    @CircuitBreaker(name = "documentAi", fallbackMethod = "fallbackProcesar")
    public Mono<DocumentAiExtraccion> procesar(String gcsPath, String mimeType) {
        if (!enabled || processorId.isBlank()) {
            log.debug("[DocumentAI] Deshabilitado o processorId vacío — omitiendo extracción para {}", gcsPath);
            return Mono.just(DocumentAiExtraccion.builder()
                    .status("OMITIDO")
                    .procesadoEn(Instant.now().toString())
                    .campos(Map.of())
                    .build());
        }

        String gcsUri = "gs://" + bucketName + "/" + gcsPath;

        return getAccessToken()
                .flatMap(token -> {
                    String processorUri = String.format(
                            "/projects/%s/locations/%s/processors/%s:process",
                            projectId, location, processorId);

                    Map<String, Object> body = Map.of(
                            "gcsDocument", Map.of(
                                    "gcsUri", gcsUri,
                                    "mimeType", mimeType
                            )
                    );

                    return webClient.post()
                            .uri(processorUri)
                            .header("Authorization", "Bearer " + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> parseResponse(response, gcsPath))
                            .doOnSuccess(r -> log.info("[DocumentAI] Extracción completada — path={} campos={}",
                                    gcsPath, r.campos().size()));
                })
                .onErrorMap(ex -> {
                    log.error("[DocumentAI] Error procesando {} — {}", gcsPath, ex.getMessage());
                    return ex;
                });
    }

    // ── Fallback para circuit breaker ─────────────────────────────────────────

    @SuppressWarnings("unused")
    private Mono<DocumentAiExtraccion> fallbackProcesar(String gcsPath, String mimeType, Throwable t) {
        log.warn("[DocumentAI] Circuit breaker activo para {} — {}", gcsPath, t.getMessage());
        return Mono.just(DocumentAiExtraccion.builder()
                .status("ERROR")
                .error("Servicio temporalmente no disponible: " + t.getMessage())
                .procesadoEn(Instant.now().toString())
                .campos(Map.of())
                .build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Obtiene un Bearer token fresco. Carga las credenciales del mismo service account
     * que usa Firebase/GCS (firebase.service-account-path); si está vacío usa ADC (Cloud Run).
     * Se ejecuta en boundedElastic para no bloquear el event loop.
     */
    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
                    GoogleCredentials base;
                    if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                        base = GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath));
                    } else {
                        base = GoogleCredentials.getApplicationDefault();
                    }
                    GoogleCredentials scoped = base.createScoped(
                            Collections.singletonList(DOCAI_SCOPE));
                    scoped.refreshIfExpired();
                    return scoped.getAccessToken().getTokenValue();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Parsea la respuesta JSON de Document AI y extrae las entidades clave
     * en un mapa plano de {tipo → valorTexto}.
     *
     * Para cada entidad se prefiere normalizedValue (valor estructurado limpio:
     * fechas ISO 8601, montos numéricos) y se usa mentionText como fallback.
     *
     * Tipos soportados: Invoice Processor, Expense Processor, Form Parser y
     * Custom Document Extractor (vouchers de transferencia bancaria).
     */
    @SuppressWarnings("unchecked")
    private DocumentAiExtraccion parseResponse(Map<?, ?> response, String gcsPath) {
        Map<String, String> campos = new HashMap<>();

        try {
            Map<?, ?> document = (Map<?, ?>) response.get("document");
            if (document != null) {
                // Texto completo del documento — necesario para Form Parser
                String docText = (String) document.get("text");

                List<?> entities = (List<?>) document.get("entities");
                if (entities != null) {
                    for (Object entityObj : entities) {
                        Map<?, ?> entity = (Map<?, ?>) entityObj;
                        String tipo = (String) entity.get("type");
                        if (tipo == null) continue;

                        // Preferir normalizedValue (valor estructurado limpio) sobre mentionText
                        String valor = extraerNormalizedValue(entity);
                        if (valor == null || valor.isBlank()) {
                            valor = (String) entity.get("mentionText");
                        }
                        if (valor != null && !valor.isBlank()) {
                            campos.put(normalizarTipo(tipo), valor.trim());
                        }

                        // Guardar también el normalizedValue por separado si difiere del mentionText
                        // (útil para fechas y montos que vienen limpios en normalizedValue)
                        String normalizado = extraerNormalizedValue(entity);
                        String mencionado  = (String) entity.get("mentionText");
                        if (normalizado != null && !normalizado.isBlank()
                                && !normalizado.equals(mencionado)) {
                            campos.put(normalizarTipo(tipo) + "_normalizado", normalizado.trim());
                        }
                    }
                }

                // Fallback: si no hay entities, extraer form fields (Form Parser)
                if (campos.isEmpty()) {
                    List<?> pages = (List<?>) document.get("pages");
                    if (pages != null && docText != null) {
                        extraerFormFields(pages, docText, campos);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DocumentAI] Error parseando respuesta para {}: {}", gcsPath, e.getMessage());
        }

        return DocumentAiExtraccion.builder()
                .status(campos.isEmpty() ? "COMPLETADO_SIN_CAMPOS" : "COMPLETADO")
                .procesadorId(processorId)
                .campos(campos)
                .procesadoEn(Instant.now().toString())
                .build();
    }

    /**
     * Extrae el valor de normalizedValue de una entidad Document AI.
     * Contiene valores estructurados: fechas en ISO 8601, montos en decimal, etc.
     */
    @SuppressWarnings("unchecked")
    private String extraerNormalizedValue(Map<?, ?> entity) {
        Map<?, ?> nv = (Map<?, ?>) entity.get("normalizedValue");
        if (nv == null) return null;
        // Para montos: moneyValue.units + moneyValue.currencyCode
        Map<?, ?> money = (Map<?, ?>) nv.get("moneyValue");
        if (money != null) {
            Object units = money.get("units");
            Object nanos = money.get("nanos");
            Object currency = money.get("currencyCode");
            if (units != null) {
                String monto = units.toString();
                if (nanos != null && !nanos.toString().equals("0")) {
                    monto += "." + String.format("%09d", Long.parseLong(nanos.toString())).replaceAll("0+$", "");
                }
                return currency != null ? monto + " " + currency : monto;
            }
        }
        // Para fechas: dateValue con year/month/day
        Map<?, ?> date = (Map<?, ?>) nv.get("dateValue");
        if (date != null) {
            Object y = date.get("year"), m = date.get("month"), d = date.get("day");
            if (y != null && m != null && d != null) {
                return String.format("%04d-%02d-%02d",
                        Integer.parseInt(y.toString()),
                        Integer.parseInt(m.toString()),
                        Integer.parseInt(d.toString()));
            }
        }
        // Para valores de texto estructurado
        String text = (String) nv.get("text");
        return text;
    }

    /**
     * Extrae key-value pairs del Form Parser usando document.text y textSegments.
     * La API de Document AI NO provee un campo "content" directo en textAnchor;
     * el texto se obtiene recortando document.text con startIndex/endIndex.
     */
    @SuppressWarnings("unchecked")
    private void extraerFormFields(List<?> pages, String docText, Map<String, String> campos) {
        for (Object pageObj : pages) {
            Map<?, ?> page = (Map<?, ?>) pageObj;
            List<?> formFields = (List<?>) page.get("formFields");
            if (formFields == null) continue;
            for (Object fieldObj : formFields) {
                Map<?, ?> field = (Map<?, ?>) fieldObj;
                String key   = extraerTextoConSegmentos(field.get("fieldName"), docText);
                String value = extraerTextoConSegmentos(field.get("fieldValue"), docText);
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    String keyNorm = key.trim().toLowerCase()
                            .replace(":", "").replace(" ", "_");
                    campos.put(keyNorm, value.trim());
                }
            }
        }
    }

    /**
     * Extrae el texto de un Layout usando textAnchor.textSegments + document.text.
     * Este es el método correcto según la API de Document AI.
     */
    @SuppressWarnings("unchecked")
    private String extraerTextoConSegmentos(Object layoutObj, String docText) {
        if (!(layoutObj instanceof Map<?, ?> layout)) return null;
        Map<?, ?> textAnchor = (Map<?, ?>) layout.get("textAnchor");
        if (textAnchor == null) return null;
        List<?> segments = (List<?>) textAnchor.get("textSegments");
        if (segments == null || segments.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Object segObj : segments) {
            Map<?, ?> seg = (Map<?, ?>) segObj;
            // startIndex puede estar ausente (= 0) en el primer segmento
            int start = seg.get("startIndex") != null
                    ? Integer.parseInt(seg.get("startIndex").toString()) : 0;
            int end   = seg.get("endIndex") != null
                    ? Integer.parseInt(seg.get("endIndex").toString()) : 0;
            if (end > start && end <= docText.length()) {
                sb.append(docText, start, end);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Normaliza tipos de entidades de Document AI a nombres en español.
     * Cubre Invoice Processor, Expense Processor y tipos comunes de
     * vouchers de transferencia bancaria (BCP, Interbank, BBVA, Scotiabank, etc.).
     */
    private String normalizarTipo(String tipo) {
        return switch (tipo) {
            // ── Factura / Invoice Processor ──────────────────────────────────
            case "total_amount"             -> "monto";
            case "invoice_date"             -> "fechaEmision";
            case "due_date"                 -> "fechaVencimiento";
            case "invoice_id"               -> "numeroDocumento";
            case "supplier_name"            -> "emisor";
            case "supplier_tax_id"          -> "rucEmisor";
            case "receiver_name"            -> "receptor";
            case "receiver_tax_id"          -> "rucReceptor";
            case "payment_terms"            -> "condicionesPago";
            case "net_amount"               -> "subtotal";
            case "total_tax_amount"         -> "igv";
            case "receiver_account_number"  -> "cuentaBancaria";
            case "bank_name"                -> "banco";
            case "ship_to_name"             -> "destinatario";
            case "ship_to_address"          -> "direccionDestinatario";
            case "supplier_address"         -> "direccionEmisor";
            case "receiver_address"         -> "direccionReceptor";
            case "currency"                 -> "moneda";
            case "purchase_order"           -> "ordenCompra";
            case "line_item"                -> "lineaItem";

            // ── Pago / Transferencia bancaria ─────────────────────────────────
            case "payment_date"             -> "fechaPago";
            case "payment_amount"           -> "montoPagado";
            case "payment_method"           -> "canalPago";
            case "payment_type"             -> "tipoPago";
            case "payee_name"               -> "beneficiario";
            case "payer_name"               -> "pagador";
            case "payee_account_number"     -> "cuentaBeneficiario";
            case "payer_account_number"     -> "cuentaPagador";
            case "payee_bank"               -> "bancoBeneficiario";
            case "payer_bank"               -> "bancoPagador";
            case "transaction_id"           -> "numeroOperacion";
            case "reference_number"         -> "numeroReferencia";
            case "operation_number"         -> "numeroOperacion";
            case "voucher_number"           -> "numeroVoucher";
            case "confirmation_number"      -> "numeroConfirmacion";
            case "transaction_date"         -> "fechaTransaccion";
            case "transaction_amount"       -> "montoTransaccion";
            case "transaction_description"  -> "descripcion";
            case "description"              -> "descripcion";
            case "concept"                  -> "concepto";
            case "channel"                  -> "canal";
            case "account_number"           -> "numeroCuenta";
            case "account_type"             -> "tipoCuenta";
            case "cci"                       -> "cci";
            case "interbank_account"        -> "cci";

            // ── Expense Processor ─────────────────────────────────────────────
            case "expense_date"             -> "fechaGasto";
            case "expense_description"      -> "descripcionGasto";
            case "total"                    -> "total";
            case "subtotal"                 -> "subtotal";
            case "tax"                      -> "impuesto";
            case "tip"                      -> "propina";
            case "vendor_name"              -> "proveedor";
            case "vendor_address"           -> "direccionProveedor";
            case "vendor_phone"             -> "telefonoProveedor";
            case "receipt_date"             -> "fechaRecibo";
            case "receipt_id"               -> "numeroRecibo";

            // ── Genérico ──────────────────────────────────────────────────────
            default -> tipo.replace("/", "_").replace("-", "_");
        };
    }
}
