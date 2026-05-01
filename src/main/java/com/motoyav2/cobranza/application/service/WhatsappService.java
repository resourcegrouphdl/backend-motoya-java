package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ActualizarEstadoMensajeUseCase;
import com.motoyav2.cobranza.application.port.in.EnviarMensajeWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.command.EnviarMensajeWhatsappCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.application.port.out.PlantillaWhatsappPort;
import com.motoyav2.cobranza.application.port.out.WhatsAppSenderPort;
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
    private final CasoCobranzaPort casoPort;
    private final WhatsAppSenderPort waSender;

    // -------------------------------------------------------------------------
    // EnviarMensajeWhatsappUseCase
    // -------------------------------------------------------------------------

    @Override
    public Mono<String> ejecutar(EnviarMensajeWhatsappCommand command) {
        boolean esLibre = command.plantillaId() == null || command.plantillaId().isBlank();
        if (esLibre) {
            if (command.mensajeLibre() == null || command.mensajeLibre().isBlank()) {
                return Mono.error(new IllegalArgumentException("Debe indicar plantillaId o mensajeLibre"));
            }
            return guardarEnviarYAuditar(command, null, "Mensaje personalizado", command.mensajeLibre());
        }
        return plantillaPort.findById(command.plantillaId())
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada: " + command.plantillaId())))
                .flatMap(plantilla -> {
                    String mensajeReal = reemplazarVariables(plantilla.getCuerpo(), command.variables());
                    return guardarEnviarYAuditar(command, command.plantillaId(), plantilla.getNombre(), mensajeReal);
                });
    }

    private Mono<String> guardarEnviarYAuditar(EnviarMensajeWhatsappCommand cmd,
                                                String plantillaId, String plantillaNombre, String mensajeReal) {
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
                        saved.setEstado("ENVIADO");
                        return mensajePort.save(saved);
                    }
                    return waSender.enviarTexto(cmd.telefono(), mensajeReal)
                            .flatMap(wamid -> {
                                saved.setWamid(wamid.isBlank() ? null : wamid);
                                saved.setEstado(wamid.isBlank() ? "FALLIDO" : "ENVIADO");
                                return mensajePort.save(saved);
                            });
                })
                .flatMap(saved -> {
                    EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                            .contratoId(cmd.contratoId())
                            .tipo("MENSAJE_WHATSAPP")
                            .payload(Map.of(
                                    "plantillaId", plantillaId != null ? plantillaId : "LIBRE",
                                    "mensajeEnviado", mensajeReal,
                                    "estadoEnvio", saved.getEstado()
                            ))
                            .usuarioId(cmd.agenteId())
                            .usuarioNombre(cmd.agenteNombre() != null ? cmd.agenteNombre() : cmd.agenteId())
                            .automatico(false)
                            .creadoEn(new Date())
                            .build();
                    return eventoPort.append(cmd.contratoId(), evento).thenReturn(saved.getId());
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

    /**
     * Busca el contratoId cuyo clienteTelefono coincide con el número entrante.
     * Normaliza el número a +51XXXXXXXXX para comparar.
     */
    public Mono<String> encontrarContratoIdPorTelefono(String telefono) {
        String normalizado = normalizarTelefono(telefono);
        return casoPort.findAll()
                .filter(c -> c.getClienteTelefono() != null
                        && normalizarTelefono(c.getClienteTelefono()).equals(normalizado))
                .next()
                .map(c -> c.getContratoId())
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado para teléfono: " + telefono)));
    }

    /** Almacena un mensaje de texto entrante del cliente como INBOUND. */
    public Mono<Void> registrarMensajeEntrante(String contratoId, String clienteNombre,
                                                String telefono, String wamid,
                                                String texto, Date recibidoEn) {
        MensajeWhatsappDocument doc = MensajeWhatsappDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(contratoId)
                .clienteNombre(clienteNombre)
                .telefono(telefono)
                .direction("INBOUND")
                .wamid(wamid)
                .textoRecibido(texto)
                .estado("RECIBIDO")
                .recibidoEn(recibidoEn)
                .automatico(false)
                .build();
        return mensajePort.save(doc).then();
    }

    /**
     * Almacena un mensaje con imagen/documento entrante como INBOUND.
     * esVoucher arranca en false — ProcesarVoucherWhatsappService lo actualiza
     * a true si supera el umbral de confianza del OCR.
     * Retorna el ID del documento creado para que el service de vouchers pueda actualizarlo.
     */
    public Mono<String> registrarMediaEntrante(String contratoId, String clienteNombre, String telefono,
                                               String mediaUrl, String mediaType, Date recibidoEn) {
        MensajeWhatsappDocument doc = MensajeWhatsappDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(contratoId)
                .clienteNombre(clienteNombre)
                .telefono(telefono)
                .direction("INBOUND")
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .esVoucher(false)
                .estado("RECIBIDO")
                .recibidoEn(recibidoEn)
                .automatico(false)
                .build();
        return mensajePort.save(doc).map(MensajeWhatsappDocument::getId);
    }

    /** Incrementa el contador de no leídos y actualiza ultimaRespuestaCliente en el caso. */
    public Mono<Void> actualizarRespuestaCliente(String contratoId) {
        return casoPort.findById(contratoId)
                .flatMap(caso -> {
                    caso.setUltimaRespuestaCliente(new Date());
                    caso.setMensajesNoLeidos(
                            caso.getMensajesNoLeidos() != null ? caso.getMensajesNoLeidos() + 1 : 1);
                    return casoPort.save(caso);
                })
                .then();
    }

    /** Resetea el contador de mensajes no leídos (llamar cuando el agente abre el chat). */
    public Mono<Void> marcarMensajesLeidos(String contratoId) {
        return casoPort.findById(contratoId)
                .flatMap(caso -> {
                    caso.setMensajesNoLeidos(0);
                    return casoPort.save(caso);
                })
                .then();
    }

    private String normalizarTelefono(String tel) {
        if (tel == null) return "";
        String digits = tel.replaceAll("\\D", "");
        if (digits.startsWith("51") && digits.length() == 11) return "+" + digits;
        if (digits.length() == 9) return "+51" + digits;
        return "+" + digits;
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
