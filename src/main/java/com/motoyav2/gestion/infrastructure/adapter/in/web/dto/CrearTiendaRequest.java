package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearTiendaRequest(

        // Representante legal
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
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
        Double latitude,
        Double longitude,
        String notes
) {}
