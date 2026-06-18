package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.application.port.out.PlantillaWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PlantillaWhatsappDocument;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consultas de solo lectura sobre mensajes y plantillas WA.
 * Extraído de WhatsappService (responsabilidad: lectura).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MensajeWaQueryService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final PlantillaWhatsappPort plantillaPort;
    private final MensajeWhatsappPort   mensajePort;

    public Flux<PlantillaWhatsappDocument> listarPlantillas() {
        return plantillaPort.findActivas();
    }

    public Flux<MensajeWhatsappDocument> listarMensajes(String contratoId) {
        return mensajePort.findByContratoId(contratoId);
    }

    public Mono<String> preview(String contratoId, String plantillaId, Map<String, String> variables) {
        return plantillaPort.findById(plantillaId)
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada: " + plantillaId)))
                .map(p -> reemplazarVariables(p.getCuerpo(), variables));
    }

    private String reemplazarVariables(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return template;
        StringBuffer sb = new StringBuffer();
        Matcher matcher = VAR_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value   = variables.getOrDefault(varName, "{{" + varName + "}}");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
