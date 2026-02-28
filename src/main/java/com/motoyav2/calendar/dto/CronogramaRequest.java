package com.motoyav2.calendar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Cuerpo del request para generar un cronograma de cuotas en Google Calendar
 * e iniciar el caso de cobranza correspondiente.
 *
 * El campo {@code nombreCliente} debe venir en el formato:
 * {@code "Apellido1 Apellido2 Nombre1 S/.{monto}"} — el monto es extraído por el servicio.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Data
public class CronogramaRequest {

    /**
     * Nombre completo del cliente + monto por cuota.
     * Formato: "Garzon Aranzo Jhonatan S/.235" ó "Garzon Aranzo Jhonatan S/235"
     */
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

    // ── Campos para iniciar el caso de cobranza ──────────────────────────────

    /** ID del contrato, p.ej. "CTR-1001". Si es null no se crea el caso de cobranza. */
    private String contratoId;

    /** ID de la tienda / store */
    private String storeId;

    /** ID del agente asignado (opcional) */
    private String agenteAsignadoId;

    /** Nombre del agente asignado (opcional) */
    private String agenteAsignadoNombre;

    /** Usuario que dispara la acción */
    private String creadoPor;
}
