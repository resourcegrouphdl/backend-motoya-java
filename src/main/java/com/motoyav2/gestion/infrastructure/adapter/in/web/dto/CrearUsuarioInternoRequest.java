package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearUsuarioInternoRequest(

        @NotBlank String firstName,
        @NotBlank String lastName,

        @NotBlank @Email String email,

        @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        /** admin | supervisor | evaluador | asesor */
        @NotBlank String userType,

        String phone,
        String documentType,
        String documentNumber,

        /**
         * Lista de módulos a los que tendrá acceso.
         * Si viene vacía/null se aplican los defaults del rol.
         */
        List<String> modulos
) {}
