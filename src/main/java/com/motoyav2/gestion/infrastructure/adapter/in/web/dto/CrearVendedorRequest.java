package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearVendedorRequest(

        // Datos personales
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String phone,
        String documentType,
        String documentNumber,

        // Asignación (obligatorio)
        @NotBlank String tiendaId,
        @NotBlank String position,

        // Laboral
        Double commissionRate,
        Double salesGoal,
        String employeeId,
        String supervisorId,
        Integer experience,
        String education,

        // Contacto de emergencia
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelationship,

        // Adicional
        String address,
        String city,
        String district,
        String gender,
        String notes
) {}
