package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ActualizarVendedorRequest(

        // Datos personales (sin email ni password — son operaciones separadas en Firebase Auth)
        @NotBlank String firstName,
        @NotBlank String lastName,
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
