package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record VendedorResponse(
        String uid,

        // Datos personales
        String firstName,
        String lastName,
        String email,
        String phone,
        @JsonInclude(JsonInclude.Include.NON_NULL) String documentType,
        @JsonInclude(JsonInclude.Include.NON_NULL) String documentNumber,

        // Tienda
        String tiendaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String tiendaBusinessName,

        // Laboral
        String position,
        String vendedorStatus,
        Boolean isActive,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double commissionRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double salesGoal,
        @JsonInclude(JsonInclude.Include.NON_NULL) String employeeId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String supervisorId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer experience,
        @JsonInclude(JsonInclude.Include.NON_NULL) String education,

        // Contacto de emergencia
        @JsonInclude(JsonInclude.Include.NON_NULL) String emergencyContactName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String emergencyContactPhone,
        @JsonInclude(JsonInclude.Include.NON_NULL) String emergencyContactRelationship,

        // Adicional
        @JsonInclude(JsonInclude.Include.NON_NULL) String address,
        @JsonInclude(JsonInclude.Include.NON_NULL) String city,
        @JsonInclude(JsonInclude.Include.NON_NULL) String district,
        @JsonInclude(JsonInclude.Include.NON_NULL) String gender,
        @JsonInclude(JsonInclude.Include.NON_NULL) String notes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String createdBy
) {}
