package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ActualizarTiendaRequest(

        // Representante legal (sin email ni password — son operaciones separadas en Firebase Auth)
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone,
        String documentType,
        String documentNumber,

        // Negocio (obligatorios mínimos)
        @NotBlank String businessName,
        @NotBlank String city,
        @NotBlank String contactPersonName,
        @NotBlank String contactPersonPhone,

        // Opcionales
        String taxId,
        String address,
        String district,
        String postalCode,
        String bankAccount,
        String legalRepresentative,
        String website,
        String facebook,
        String instagram,
        String whatsapp,
        String notes
) {}
