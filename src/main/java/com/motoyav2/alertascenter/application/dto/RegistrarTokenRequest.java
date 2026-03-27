package com.motoyav2.alertascenter.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistrarTokenRequest(@NotBlank String token) {}
