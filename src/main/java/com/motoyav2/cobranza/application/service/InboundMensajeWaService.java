package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.UUID;

/**
 * Gestión de mensajes INBOUND: registro, actualización de contadores, marcar leídos.
 * Extraído de WhatsappService (responsabilidad: mensajes entrantes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundMensajeWaService {

    private final MensajeWhatsappPort mensajePort;
    private final CasoCobranzaPort    casoPort;

    /** Registra texto entrante con ventana de servicio 24h poblada. */
    public Mono<Void> registrarMensajeEntrante(String contratoId, String clienteNombre,
                                                String telefono, String wamid,
                                                String texto, Date recibidoEn) {
        Date inicio = recibidoEn != null ? recibidoEn : new Date();
        Date expira = new Date(inicio.getTime() + 24L * 60 * 60 * 1000);
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
                .ventanaServicioInicio(inicio)
                .ventanaServicioExpira(expira)
                .dentroVentanaServicio(true)
                .categoriaPrecio("SERVICE")
                .billable(false)
                .build();
        return mensajePort.save(doc).then();
    }

    /** Registra media entrante con ventana de servicio 24h poblada. Devuelve el ID del documento. */
    public Mono<String> registrarMediaEntrante(String contratoId, String clienteNombre, String telefono,
                                               String mediaUrl, String mediaType, Date recibidoEn) {
        Date inicio = recibidoEn != null ? recibidoEn : new Date();
        Date expira = new Date(inicio.getTime() + 24L * 60 * 60 * 1000);
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
                .ventanaServicioInicio(inicio)
                .ventanaServicioExpira(expira)
                .dentroVentanaServicio(true)
                .categoriaPrecio("SERVICE")
                .billable(false)
                .build();
        return mensajePort.save(doc).map(MensajeWhatsappDocument::getId);
    }

    /** Incrementa mensajes no leídos y actualiza ultimaRespuestaCliente. */
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

    /** Resetea el contador de mensajes no leídos. */
    public Mono<Void> marcarMensajesLeidos(String contratoId) {
        return casoPort.findById(contratoId)
                .flatMap(caso -> {
                    caso.setMensajesNoLeidos(0);
                    return casoPort.save(caso);
                })
                .then();
    }
}
