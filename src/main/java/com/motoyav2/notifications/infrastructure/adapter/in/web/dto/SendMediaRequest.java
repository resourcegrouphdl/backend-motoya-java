package com.motoyav2.notifications.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request para enviar un archivo (imagen, documento, video, audio) por WhatsApp.
 * El archivo debe estar previamente subido a Firebase Storage.
 *
 * Ejemplo body:
 * {
 *   "recipient": "51987654321",
 *   "storagePath": "contratos/contratos-pdf/abc123/contrato.pdf",
 *   "mediaType": "document",
 *   "filename": "contrato.pdf",
 *   "caption": "Aquí está tu contrato firmado 📄",
 *   "contratoId": "abc123"
 * }
 *
 * mediaType válidos: "image" | "document" | "video" | "audio"
 */
public record SendMediaRequest(

        @NotBlank(message = "El destinatario es requerido")
        String recipient,

        @NotBlank(message = "El path en Storage es requerido")
        String storagePath,

        @NotBlank(message = "El tipo de media es requerido: image, document, video, audio")
        String mediaType,

        String filename,

        String caption,

        String contratoId
) {}
