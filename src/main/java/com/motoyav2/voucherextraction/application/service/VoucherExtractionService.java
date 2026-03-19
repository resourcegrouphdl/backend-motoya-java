package com.motoyav2.voucherextraction.application.service;

import com.motoyav2.voucherextraction.application.port.in.ExtraerVoucherUseCase;
import com.motoyav2.voucherextraction.application.port.out.DocumentAiRawPort;
import com.motoyav2.voucherextraction.application.port.out.LlmEnriquecimientoPort;
import com.motoyav2.voucherextraction.domain.model.VoucherExtraccion;
import com.motoyav2.voucherextraction.domain.model.VoucherRaw;
import com.motoyav2.voucherextraction.domain.strategy.BancoStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquestador principal del flujo de extracción de vouchers.
 *
 * Flujo:
 *   1. Obtiene texto completo + formFields + entities de Document AI (DocumentAiRawPort)
 *   2. Normaliza y mergea los campos de Form Parser como base
 *   3. Detecta el banco y aplica la estrategia regex correspondiente (override)
 *   4. Si faltan campos críticos y LLM está habilitado, llama a Claude Haiku
 *   5. Retorna VoucherExtraccion con todos los campos consolidados
 */
@Slf4j
@Service
public class VoucherExtractionService implements ExtraerVoucherUseCase {

    /** Campos sin los cuales el voucher es considerado incompleto → activan Claude. */
    private static final Set<String> CAMPOS_CRITICOS = Set.of(
            "montoPagado", "fechaPago", "numeroOperacion");

    private final DocumentAiRawPort documentAiRawPort;
    private final LlmEnriquecimientoPort llmEnriquecimientoPort;
    private final BancoStrategyRegistry strategyRegistry;

    @Value("${app.documentai.enabled:false}")
    private boolean documentAiEnabled;

    @Value("${app.anthropic.enabled:true}")
    private boolean llmEnabled;

    public VoucherExtractionService(DocumentAiRawPort documentAiRawPort,
                                    LlmEnriquecimientoPort llmEnriquecimientoPort,
                                    BancoStrategyRegistry strategyRegistry) {
        this.documentAiRawPort = documentAiRawPort;
        this.llmEnriquecimientoPort = llmEnriquecimientoPort;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public Mono<VoucherExtraccion> extraer(String gcsPath, String mimeType) {
        if (!documentAiEnabled) {
            log.debug("[VoucherExtraction] Document AI deshabilitado — omitiendo {}", gcsPath);
            return Mono.just(VoucherExtraccion.omitido());
        }

        log.info("[VoucherExtraction] Solicitando raw a DocumentAI — gcsPath={} mimeType={}", gcsPath, mimeType);
        return documentAiRawPort.obtenerRaw(gcsPath, mimeType)
                .doOnNext(raw -> log.info("[VoucherExtraction] Raw recibido — textLen={} formFields={} entities={}",
                        raw.fullText() != null ? raw.fullText().length() : 0,
                        raw.formFields().size(), raw.entities().size()))
                .flatMap(this::procesarRaw)
                .doOnNext(result -> log.info("[VoucherExtraction] Resultado final — status={} banco={} campos={} llm={}",
                        result.status(), result.banco(),
                        result.campos() != null ? result.campos().size() : 0,
                        result.enriquecidoConLlm()))
                .onErrorResume(ex -> {
                    log.error("[VoucherExtraction] Error procesando {} — {}", gcsPath, ex.getMessage(), ex);
                    return Mono.just(VoucherExtraccion.error(ex.getMessage()));
                });
    }

    // ── Pipeline interno ──────────────────────────────────────────────────────

    private Mono<VoucherExtraccion> procesarRaw(VoucherRaw raw) {

        // 1. Base: campos de Form Parser normalizados (formFields < entities, entities tienen precedencia)
        Map<String, String> campos = new LinkedHashMap<>();
        raw.formFields().forEach((k, v) -> campos.put(normalizeKey(k), v));
        raw.entities().forEach((k, v) -> campos.put(normalizeKey(k), v));

        // 2. Estrategia por banco (regex) — override sobre Form Parser
        BancoStrategy strategy = strategyRegistry.findStrategy(raw.fullText());
        Map<String, String> regexCampos = strategy.extraer(raw.fullText());
        campos.putAll(regexCampos);

        log.info("[VoucherExtraction] banco={} camposFormParser={} camposRegex={} camposTotal={}",
                strategy.getBancoNombre(),
                raw.formFields().size() + raw.entities().size(),
                regexCampos.size(),
                campos.size());

        // 3. Enriquecer con LLM si faltan campos críticos
        Set<String> faltantes = CAMPOS_CRITICOS.stream()
                .filter(c -> !campos.containsKey(c) || campos.get(c).isBlank())
                .collect(Collectors.toSet());

        boolean textoDisponible = raw.fullText() != null && !raw.fullText().isBlank();

        if (!faltantes.isEmpty() && llmEnabled && textoDisponible) {
            log.info("[VoucherExtraction] Campos críticos faltantes={} — invocando LLM", faltantes);
            return llmEnriquecimientoPort.enriquecer(raw.fullText(), faltantes)
                    .map(llmCampos -> {
                        // LLM solo completa lo que falta, no sobreescribe
                        llmCampos.forEach(campos::putIfAbsent);
                        log.info("[VoucherExtraction] LLM aportó {} campos nuevos", llmCampos.size());
                        return buildExtraccion(campos, strategy.getBancoNombre(), true);
                    })
                    .onErrorResume(ex -> {
                        log.warn("[VoucherExtraction] LLM falló, usando solo regex — {}", ex.getMessage());
                        return Mono.just(buildExtraccion(campos, strategy.getBancoNombre(), false));
                    });
        }

        return Mono.just(buildExtraccion(campos, strategy.getBancoNombre(), false));
    }

    private VoucherExtraccion buildExtraccion(Map<String, String> campos, String banco, boolean llmUsed) {
        String status = campos.isEmpty() ? "COMPLETADO_SIN_CAMPOS" : "COMPLETADO";
        return VoucherExtraccion.builder()
                .status(status)
                .banco(banco)
                .campos(Collections.unmodifiableMap(campos))
                .enriquecidoConLlm(llmUsed)
                .procesadoEn(Instant.now().toString())
                .build();
    }

    /**
     * Normaliza claves de Form Parser a camelCase español.
     * "Monto pagado" → "montoPagado"   "N° de operación" → "nDeOperacion"
     */
    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) return "campo";
        String clean = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")           // quitar tildes
                .replaceAll("[^a-zA-Z0-9\\s]", "")  // quitar especiales
                .trim();
        String[] words = clean.split("\\s+");
        if (words.length == 0 || words[0].isBlank()) return raw.toLowerCase().replace(" ", "_");
        StringBuilder sb = new StringBuilder(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isBlank()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                sb.append(words[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
