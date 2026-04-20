package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record UsuarioInternoResponse(
        String uid,
        String firstName,
        String lastName,
        String email,
        String phone,
        String documentType,
        String documentNumber,
        String userType,
        String userCategory,
        Boolean isActive,
        List<String> modulos,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String createdBy
) {}
