package com.motoyav2.notifications.infrastructure.adapter.in.web.dto;

import com.motoyav2.notifications.domain.model.NotificationTemplate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request dedicado para enviar correos electrónicos.
 *
 * Ejemplo:
 * {
 *   "to": "cliente@gmail.com",
 *   "template": "CONTRATO_ACTIVADO",
 *   "variables": {
 *     "cliente": "Juan Pérez",
 *     "numeroContrato": "C-001",
 *     "precioVehiculo": "S/ 8,500.00",
 *     "numeroCuotas": "24",
 *     "cuotaMensual": "S/ 380.00"
 *   },
 *   "contratoId": "abc123"
 * }
 */
public record SendEmailRequest(

        @NotBlank(message = "El correo destinatario es requerido")
        @Email(message = "El correo destinatario no tiene un formato válido")
        String to,

        @NotNull(message = "La plantilla es requerida")
        NotificationTemplate template,

        Map<String, String> variables,

        String contratoId
) {}
