package com.motoyav2.migracion.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompletarStagingRequest(

        @NotBlank(message = "contratoId es requerido")
        String contratoId,

        @NotBlank(message = "clienteNombre es requerido")
        @Size(min = 3, message = "clienteNombre debe tener al menos 3 caracteres")
        String clienteNombre,

        @NotBlank(message = "clienteDni es requerido")
        @Pattern(regexp = "\\d{8}", message = "clienteDni debe tener exactamente 8 dígitos")
        String clienteDni,

        @NotBlank(message = "telefono es requerido")
        @Pattern(regexp = "\\+51\\d{9}", message = "telefono debe tener formato +51XXXXXXXXX")
        String telefono,

        @NotBlank(message = "moto es requerida")
        @Size(min = 3, message = "moto debe tener al menos 3 caracteres")
        String moto
) {}
