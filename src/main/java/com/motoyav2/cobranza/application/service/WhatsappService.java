package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ActualizarEstadoMensajeUseCase;
import com.motoyav2.cobranza.application.port.in.EnviarMensajeWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.command.EnviarMensajeWhatsappCommand;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.application.port.out.PlantillaWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PlantillaWhatsappDocument;
import com.motoyav2.shared.exception.NotFoundException;
import reactor.core.publisher.Flux;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappService implements EnviarMensajeWhatsappUseCase, ActualizarEstadoMensajeUseCase {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final PlantillaWhatsappPort plantillaPort;
    private final MensajeWhatsappPort mensajePort;
    private final EventoCobranzaPort eventoPort;

    // TODO: inyectar TwilioService cuando esté implementado
    // private final TwilioService twilioService;

    // -------------------------------------------------------------------------
    // EnviarMensajeWhatsappUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<String> ejecutar(EnviarMensajeWhatsappCommand command) {
        return plantillaPort.findById(command.plantillaId())
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada: " + command.plantillaId())))
                .flatMap(plantilla -> {
                    String mensajeReal = reemplazarVariables(plantilla.getCuerpo(), command.variables());

                    MensajeWhatsappDocument mensaje = MensajeWhatsappDocument.builder()
                            .id(UUID.randomUUID().toString())
                            .contratoId(command.contratoId())
                            .telefono(command.telefono())
                            .plantillaId(command.plantillaId())
                            .plantillaNombre(plantilla.getNombre())
                            .mensajeReal(mensajeReal)
                            .estado("PENDIENTE")
                            .automatico(false)
                            .enviadoPor(command.agenteId())
                            .storeId(command.storeId())
                            .enviadoEn(new Date())
                            .build();

                    return mensajePort.save(mensaje)
                            .flatMap(savedMensaje -> {
                                // TODO: llamar twilioService.sendWhatsApp(command.telefono(), mensajeReal)
                                //   .flatMap(twilioResponse -> {
                                //       savedMensaje.setWamid(twilioResponse.getSid());
                                //       savedMensaje.setEstado("ENVIADO");
                                //       return mensajePort.save(savedMensaje);
                                //   })
                                //
                                // Por ahora se marca como ENVIADO directamente:
                                savedMensaje.setEstado("ENVIADO");
                                return mensajePort.save(savedMensaje);
                            })
                            .flatMap(savedMensaje -> {
                                EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                                        .contratoId(command.contratoId())
                                        .tipo("MENSAJE_WHATSAPP")
                                        .payload(Map.of(
                                                "plantillaId", command.plantillaId(),
                                                "mensajeEnviado", mensajeReal,
                                                "estadoEnvio", "ENVIADO"
                                        ))
                                        .usuarioId(command.agenteId())
                                        .usuarioNombre(command.agenteNombre() != null ? command.agenteNombre() : command.agenteId())
                                        .automatico(false)
                                        .creadoEn(new Date())
                                        .build();

                                return eventoPort.append(command.contratoId(), evento)
                                        .thenReturn(savedMensaje.getId());
                            });
                });
    }

    // -------------------------------------------------------------------------
    // ActualizarEstadoMensajeUseCase — webhook Twilio
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> ejecutar(String wamid, String nuevoEstado, Date timestamp) {
        return mensajePort.findByWamid(wamid)
                // Si no se encuentra el wamid → ignorar silenciosamente
                .flatMap(mensaje -> {
                    mensaje.setEstado(nuevoEstado);
                    switch (nuevoEstado) {
                        case "ENTREGADO" -> mensaje.setEntregadoEn(timestamp);
                        case "LEIDO"     -> mensaje.setLeidoEn(timestamp);
                    }
                    return mensajePort.save(mensaje);
                })
                .then();
    }

    // -------------------------------------------------------------------------
    // Read-only queries — consumed by CobranzaController
    // -------------------------------------------------------------------------

    public Flux<PlantillaWhatsappDocument> listarPlantillas() {
        return plantillaPort.findActivas();
    }

    public Mono<String> preview(String contratoId, String plantillaId, Map<String, String> variables) {
        return plantillaPort.findById(plantillaId)
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada: " + plantillaId)))
                .map(plantilla -> reemplazarVariables(plantilla.getCuerpo(), variables));
    }

    public Flux<MensajeWhatsappDocument> listarMensajes(String contratoId) {
        return mensajePort.findByContratoId(contratoId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String reemplazarVariables(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return template;
        StringBuffer sb = new StringBuffer();
        Matcher matcher = VAR_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "{{" + varName + "}}");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
