package com.motoyav2.cobranza.application.port.in;

import reactor.core.publisher.Mono;

/**
 * Orquesta el flujo completo cuando un cliente envía una imagen de comprobante
 * de pago a través del chat de WhatsApp:
 *   1. Descarga la imagen desde Factiliza → sube a GCS
 *   2. Extrae datos con Document AI + Claude
 *   3. Registra el Voucher en cobranzas (estado PENDIENTE)
 *   4. Crea alerta para el agente con comparación monto_detectado vs monto_cuota
 *   5. Confirma recepción al cliente por WhatsApp
 */
public interface ProcesarVoucherWhatsappUseCase {

    /**
     * @param contratoId      ID del contrato activo del cliente
     * @param storeId         ID de la tienda del caso
     * @param clienteNombre   Nombre del titular (para la notificación de confirmación)
     * @param clienteTelefono Teléfono normalizado del cliente (formato +51XXXXXXXXX)
     * @param mediaUrl        URL de descarga del comprobante desde Factiliza
     * @param mediaType       Tipo de media: "image" o "document"
     * @param mensajeId       ID del MensajeWhatsappDocument guardado previamente
     */
    Mono<Void> procesar(String contratoId, String storeId,
                        String clienteNombre, String clienteTelefono,
                        String mediaUrl, String mediaType, String mensajeId);
}
