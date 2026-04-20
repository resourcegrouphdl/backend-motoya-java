package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record TiendaResponse(
        String uid,
        String firstName,
        String lastName,
        String email,
        String phone,
        String businessName,
        String taxId,
        String city,
        String address,
        String district,
        String tiendaStatus,
        Boolean isActive,
        String contactPersonName,
        String contactPersonPhone,
        @JsonInclude(JsonInclude.Include.NON_NULL) String bankAccount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String website,
        @JsonInclude(JsonInclude.Include.NON_NULL) String notes,
        String createdBy
) {}
