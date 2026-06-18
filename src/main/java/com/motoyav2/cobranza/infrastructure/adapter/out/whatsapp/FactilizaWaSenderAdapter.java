package com.motoyav2.cobranza.infrastructure.adapter.out.whatsapp;

import com.motoyav2.cobranza.application.port.out.WhatsAppSenderPort;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Adaptador de salida que implementa WhatsAppSenderPort delegando a MetaWhatsAppNotificationAdapter.
 *
 * Los errores se PROPAGAN al caller (WhatsappService) para que:
 *   - Se registre el error en Firestore (estado FALLIDO + errorDetalle)
 *   - El endpoint HTTP devuelva un error real al frontend
 *
 * No swallowing de errores — el frontend DEBE ver el fallo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FactilizaWaSenderAdapter implements WhatsAppSenderPort {

    private final MetaWhatsAppNotificationAdapter meta;

    @Override
    public Mono<String> enviarTexto(String telefono, String texto) {
        log.info("[WA-SENDER] enviarTexto to={} textLen={}", telefono, texto != null ? texto.length() : 0);
        return meta.sendText(telefono, texto)
                .doOnSuccess(wamid -> log.info("[WA-SENDER] Texto enviado | to={} wamid={}", telefono, wamid))
                .doOnError(e  -> log.error("[WA-SENDER] Error texto | to={} error={}", telefono, e.getMessage()));
    }

    @Override
    public Mono<String> enviarConPlantilla(String telefono, String metaTemplateName,
                                            String languageCode, List<String> paramsOrdenados) {
        log.info("[WA-SENDER] enviarConPlantilla to={} template={} params={}",
                telefono, metaTemplateName, paramsOrdenados);
        return meta.sendTemplateRaw(telefono, metaTemplateName, languageCode, paramsOrdenados)
                .doOnSuccess(wamid -> log.info("[WA-SENDER] Template enviado | to={} template={} wamid={}",
                        telefono, metaTemplateName, wamid))
                .doOnError(e  -> log.error("[WA-SENDER] Error template | to={} template={} error={}",
                        telefono, metaTemplateName, e.getMessage()));
    }
}
