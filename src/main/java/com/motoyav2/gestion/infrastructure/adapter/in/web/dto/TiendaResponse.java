package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TiendaResponse(
        String uid,

        // Representante legal
        String firstName,
        String lastName,
        String email,
        String phone,
        @JsonInclude(JsonInclude.Include.NON_NULL) String documentType,
        @JsonInclude(JsonInclude.Include.NON_NULL) String documentNumber,

        // Negocio
        String businessName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String taxId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String legalRepresentative,

        // Ubicación
        String city,
        @JsonInclude(JsonInclude.Include.NON_NULL) String address,
        @JsonInclude(JsonInclude.Include.NON_NULL) String district,
        @JsonInclude(JsonInclude.Include.NON_NULL) String postalCode,

        // Estado
        String tiendaStatus,
        Boolean isActive,

        // Contacto / comercial
        @JsonInclude(JsonInclude.Include.NON_NULL) String contactPersonName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String contactPersonPhone,
        @JsonInclude(JsonInclude.Include.NON_NULL) String bankAccount,

        // Online
        @JsonInclude(JsonInclude.Include.NON_NULL) String website,
        @JsonInclude(JsonInclude.Include.NON_NULL) String facebook,
        @JsonInclude(JsonInclude.Include.NON_NULL) String instagram,
        @JsonInclude(JsonInclude.Include.NON_NULL) String whatsapp,

        // Auditoría
        @JsonInclude(JsonInclude.Include.NON_NULL) String notes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String createdBy
) {}
