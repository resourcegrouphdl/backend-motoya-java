package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.EnviarComprobanteWhatsappCommand;
import reactor.core.publisher.Mono;

/**
 * Envía el comprobante de un voucher ya aprobado al cliente por WhatsApp.
 * Resolución de documento (prioridad):
 *  1. archivoGcsPath (PDF subido manualmente por el agente)
 *  2. comprobanteId del voucher → pdfPath del comprobante generado
 *  3. imagenPath del voucher (el original enviado por el cliente)
 *
 * Retorna el mensajeId creado en cobranzas-mensajes-whatsapp.
 */
public interface EnviarComprobanteWhatsappUseCase {
    Mono<String> ejecutar(EnviarComprobanteWhatsappCommand command);
}
