package com.motoyav2.migracion.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompletarStagingRequest(

        @NotBlank(message = "contratoId es requerido")
        String contratoId,

        @NotBlank(message = "clienteNombre es requerido")
        @Size(min = 3, message = "clienteNombre debe tener al menos 3 caracteres")
        String clienteNombre,

        // DNI: 8 dígitos | CE: 9-12 alfanumérico | Pasaporte: variable
        String titularTipoDocumento,

        @NotBlank(message = "clienteDni es requerido")
        @Size(min = 7, max = 15, message = "clienteDni debe tener entre 7 y 15 caracteres")
        String clienteDni,

        // Formato flexible: acepta +51XXXXXXXXX, 9XXXXXXXX, números con espacios/guiones.
        // El agente puede corregir el formato desde el caso de cobranza.
        @NotBlank(message = "telefono es requerido")
        @Size(min = 7, message = "telefono debe tener al menos 7 caracteres")
        String telefono,

        @NotBlank(message = "moto es requerida")
        @Size(min = 3, message = "moto debe tener al menos 3 caracteres")
        String moto,

        // Opcionales — enriquecen la migración pero no bloquean COMPLETO
        String email,

        // Dirección del titular
        String storeId,
        String direccion,
        String distrito,
        String provincia,
        String departamento,

        // Referencias personales del cliente
        @Valid
        List<ReferenciaDto> referencias,

        // Observaciones internas del operador
        String observaciones,

        // Fiador
        String fiadorNombre,
        String fiadorApellidos,
        String fiadorTipoDocumento,
        String fiadorDni,
        String fiadorTelefono,
        String fiadorEmail,
        String fiadorParentesco

) {
    public record ReferenciaDto(
            @NotBlank(message = "nombre de la referencia es requerido")
            String nombre,
            @NotBlank(message = "teléfono de la referencia es requerido")
            String telefono,
            String parentesco,
            String direccion
    ) {}
}
