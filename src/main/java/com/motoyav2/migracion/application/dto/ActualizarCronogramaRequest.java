package com.motoyav2.migracion.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ActualizarCronogramaRequest(

        @NotEmpty(message = "cronograma no puede estar vacío")
        @Valid
        List<CuotaCorregidaDto> cronograma,

        /** Monto global de cuota — se aplica a cuotas sin monto individual. */
        Double montoCuota

) {
    public record CuotaCorregidaDto(

            @NotNull(message = "cuota es requerida")
            @Positive(message = "cuota debe ser un número positivo")
            Integer cuota,

            @NotNull(message = "fechaVencimiento es requerida")
            String fechaVencimiento,

            @NotNull(message = "pagada es requerida")
            Boolean pagada,

            /** Monto individual. Null = usar el montoCuota global del request o del staging. */
            Double monto
    ) {}
}
