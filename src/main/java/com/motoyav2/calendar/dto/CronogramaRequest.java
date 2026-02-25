package com.motoyav2.calendar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Cuerpo del request para generar un cronograma de cuotas en Google Calendar.
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Data
public class CronogramaRequest {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String nombreCliente;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    /** Estado del cronograma: PENDIENTE, PAGADO, ATRASADO */
    @NotBlank(message = "El estado es obligatorio")
    private String estado;

    @NotEmpty(message = "Debe incluir al menos una cuota")
    @Valid
    private List<CuotaRequest> cuotas;
}
