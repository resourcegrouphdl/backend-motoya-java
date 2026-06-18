package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ActualizarEstadoMensajeUseCase;
import com.motoyav2.cobranza.application.port.in.EnviarMensajeWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.RecibirVoucherUseCase;
import com.motoyav2.cobranza.application.port.in.command.EnviarMensajeWhatsappCommand;
import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.application.port.out.PlantillaWhatsappPort;
import com.motoyav2.cobranza.application.port.out.WhatsAppSenderPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import com.motoyav2.shared.util.TelefonoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsabilidades actuales (reducidas tras refactoring FASE 6):
 *   1. Enviar mensajes OUTBOUND (EnviarMensajeWhatsappUseCase)
 *   2. Actualizar estado de mensaje por wamid (ActualizarEstadoMensajeUseCase)
 *   3. Promover imagen INBOUND a voucher PENDIENTE
 *   4. Buscar contratoId por teléfono del cliente
 *
 * Responsabilidades migradas:
 *   - Registro de mensajes INBOUND  → InboundMensajeWaService
 *   - Consultas de mensajes/plantillas → MensajeWaQueryService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappService implements EnviarMensajeWhatsappUseCase, ActualizarEstadoMensajeUseCase {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final PlantillaWhatsappPort  plantillaPort;
    private final MensajeWhatsappPort    mensajePort;
    private final EventoCobranzaPort     eventoPort;
    private final CasoCobranzaPort       casoPort;
    private final WhatsAppSenderPort     waSender;
    private final RecibirVoucherUseCase  recibirVoucherUseCase;

    // ── EnviarMensajeWhatsappUseCase ──────────────────────────────────────────

    @Override
    public Mono<String> ejecutar(EnviarMensajeWhatsappCommand command) {
        // ── Mensaje libre (sin plantilla) ──────────────────────────────────────
        boolean esLibre = command.plantillaId() == null || command.plantillaId().isBlank();
        if (esLibre) {
            if (command.mensajeLibre() == null || command.mensajeLibre().isBlank()) {
                return Mono.error(new IllegalArgumentException("Debe indicar plantillaId o mensajeLibre"));
            }
            return guardarEnviarYAuditar(command, null, "Mensaje personalizado",
                    command.mensajeLibre(), null, null, null);
        }

        // ── Con plantilla ─────────────────────────────────────────────────────
        return plantillaPort.findById(command.plantillaId())
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada: " + command.plantillaId())))
                .flatMap(plantilla -> {
                    // Texto renderizado para almacenar en Firestore (preview del mensaje)
                    String mensajeReal = reemplazarVariables(plantilla.getCuerpo(), command.variables());

                    String metaTemplateName = plantilla.getMetaTemplateName();
                    if (metaTemplateName != null && !metaTemplateName.isBlank()) {
                        // ── Envío via Meta Template API (funciona fuera de ventana 24h) ──
                        List<String> params = buildParamsOrdenados(plantilla.getVariables(), command.variables());
                        log.info("[WA] Enviando via template | plantilla={} metaName={} params={}",
                                command.plantillaId(), metaTemplateName, params);
                        return guardarEnviarYAuditar(command, command.plantillaId(),
                                plantilla.getNombre(), mensajeReal, metaTemplateName, "es_PE", params);
                    } else {
                        // ── Fallback: texto plano (solo dentro de ventana 24h) ──
                        log.warn("[WA] Plantilla {} sin metaTemplateName — enviando como texto plano. " +
                                "Configura metaTemplateName en Firestore para usar Template API.",
                                command.plantillaId());
                        return guardarEnviarYAuditar(command, command.plantillaId(),
                                plantilla.getNombre(), mensajeReal, null, null, null);
                    }
                });
    }

    /**
     * Extrae los valores de variables en el orden posicional definido por la plantilla.
     * El orden de {@code variables} de Firestore es el orden posicional {{1}}, {{2}}, ...
     * que Meta espera en la Template API.
     */
    private List<String> buildParamsOrdenados(
            List<com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.VariablePlantillaDocument> variables,
            Map<String, String> valores) {
        if (variables == null) return List.of();
        List<String> params = new ArrayList<>(variables.size());
        for (var v : variables) {
            params.add(valores != null ? valores.getOrDefault(v.getNombre(), "") : "");
        }
        return params;
    }

    /**
     * Guarda el mensaje como PENDIENTE, llama a la API de WhatsApp (template o texto),
     * actualiza el estado a ENVIADO/FALLIDO y registra el evento de auditoría.
     *
     * Los errores de Meta se propagan al caller — el frontend recibirá un HTTP error real,
     * no un falso "OK". El mensaje queda en estado FALLIDO en Firestore con errorDetalle.
     */
    private Mono<String> guardarEnviarYAuditar(EnviarMensajeWhatsappCommand cmd,
                                                String plantillaId, String plantillaNombre, String mensajeReal,
                                                String metaTemplateName, String languageCode, List<String> templateParams) {
        MensajeWhatsappDocument mensaje = MensajeWhatsappDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(cmd.contratoId())
                .telefono(cmd.telefono())
                .plantillaId(plantillaId)
                .plantillaNombre(plantillaNombre)
                .mensajeReal(mensajeReal)
                .estado("PENDIENTE")
                .direction("OUTBOUND")
                .automatico(false)
                .enviadoPor(cmd.agenteId())
                .storeId(cmd.storeId())
                .enviadoEn(new Date())
                .build();

        return mensajePort.save(mensaje)
                .flatMap(saved -> {
                    if (cmd.telefono() == null || cmd.telefono().isBlank()) {
                        log.warn("[WA] Sin teléfono para contratoId={} — mensaje guardado sin enviar", cmd.contratoId());
                        saved.setEstado("FALLIDO");
                        saved.setErrorDetalle("Sin número de teléfono");
                        return mensajePort.save(saved)
                                .then(Mono.error(new IllegalArgumentException(
                                        "No hay número de teléfono para este contrato")));
                    }

                    Mono<String> sendMono = (metaTemplateName != null && !metaTemplateName.isBlank())
                            ? waSender.enviarConPlantilla(cmd.telefono(), metaTemplateName, languageCode, templateParams)
                            : waSender.enviarTexto(cmd.telefono(), mensajeReal);

                    return sendMono
                            .flatMap(wamid -> {
                                saved.setWamid(wamid.isBlank() ? null : wamid);
                                saved.setEstado("ENVIADO");
                                return mensajePort.save(saved);
                            })
                            .onErrorResume(e -> {
                                // Guarda el fallo en Firestore y propaga el error al controller
                                log.error("[WA] Fallo al enviar a {} — contratoId={}: {}",
                                        cmd.telefono(), cmd.contratoId(), e.getMessage());
                                saved.setEstado("FALLIDO");
                                saved.setErrorDetalle(e.getMessage());
                                return mensajePort.save(saved).then(Mono.error(e));
                            });
                })
                .flatMap(saved -> {
                    EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                            .contratoId(cmd.contratoId())
                            .tipo("MENSAJE_WHATSAPP")
                            .payload(Map.of(
                                    "plantillaId",    plantillaId != null ? plantillaId : "LIBRE",
                                    "mensajeEnviado", mensajeReal,
                                    "estadoEnvio",    saved.getEstado(),
                                    "metaTemplate",   metaTemplateName != null ? metaTemplateName : "texto_libre"
                            ))
                            .usuarioId(cmd.agenteId())
                            .usuarioNombre(cmd.agenteNombre() != null ? cmd.agenteNombre() : cmd.agenteId())
                            .automatico(false)
                            .creadoEn(new Date())
                            .build();
                    return eventoPort.append(cmd.contratoId(), evento).thenReturn(saved.getId());
                });
    }

    // ── ActualizarEstadoMensajeUseCase ────────────────────────────────────────

    @Override
    public Mono<Void> ejecutar(String wamid, String nuevoEstado, Date timestamp) {
        return mensajePort.findByWamid(wamid)
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

    // ── Promover imagen INBOUND a voucher ─────────────────────────────────────

    public Mono<String> registrarVoucherDesdeMensaje(String mensajeId, String contratoId, String subioPor) {
        return mensajePort.findById(mensajeId)
                .switchIfEmpty(Mono.error(new NotFoundException("Mensaje no encontrado: " + mensajeId)))
                .flatMap(msg -> {
                    if (Boolean.TRUE.equals(msg.getEsVoucher())) {
                        return Mono.just(msg.getVoucherId() != null ? msg.getVoucherId() : "");
                    }
                    if (msg.getGcsMediaUrl() == null || msg.getGcsMediaUrl().isBlank()) {
                        return Mono.error(new BadRequestException(
                                "El mensaje no tiene imagen almacenada en GCS"));
                    }
                    return casoPort.findById(contratoId)
                            .defaultIfEmpty(new CasoCobranzaDocument())
                            .flatMap(caso -> {
                                String storeId = caso.getStoreId() != null && !caso.getStoreId().isBlank()
                                        ? caso.getStoreId() : IniciarCasoService.STORE_COBRANZAS;
                                RecibirVoucherCommand command = new RecibirVoucherCommand(
                                        contratoId, storeId, msg.getGcsMediaUrl(), null,
                                        null, null, null,
                                        subioPor, "WHATSAPP_MANUAL", caso.getClienteNombre(),
                                        msg.getMediaType());
                                return recibirVoucherUseCase.ejecutar(command)
                                        .flatMap(voucherId -> {
                                            msg.setEsVoucher(true);
                                            msg.setVoucherId(voucherId);
                                            return mensajePort.save(msg).thenReturn(voucherId);
                                        });
                            });
                });
    }

    // ── Buscar contrato por teléfono (usado por endpoints legacy) ─────────────

    public Mono<String> encontrarContratoIdPorTelefono(String telefono) {
        String tel9 = TelefonoUtils.aNueveDig(telefono);
        if (tel9.isBlank()) {
            return Mono.error(new NotFoundException("Teléfono inválido: " + telefono));
        }
        return casoPort.findByClienteTelefono(tel9)
                .next()
                .map(CasoCobranzaDocument::getContratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado para teléfono: " + telefono)));
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

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
