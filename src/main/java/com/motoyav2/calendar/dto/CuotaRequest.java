package com.motoyav2.calendar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

/**
 * Representa una cuota individual dentro del cronograma.
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Data
public class CuotaRequest {

    @Positive(message = "El número de cuota debe ser positivo")
    private int numero;

    @NotNull(message = "La fecha de la cuota es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
}
