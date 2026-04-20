package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record VendedorResponse(
        String uid,
        String firstName,
        String lastName,
        String email,
        String phone,
        String tiendaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String tiendaBusinessName,
        String position,
        String vendedorStatus,
        Boolean isActive,
        Double commissionRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double salesGoal,
        String city,
        String createdBy
) {}
