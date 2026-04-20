package com.motoyav2.evaluacion.infrastructure.adapter.in.web.response;

import com.motoyav2.evaluacion.domain.model.AlertaCredito;
import com.motoyav2.evaluacion.domain.model.PersonaResumen;

import java.util.List;

/**
 * Respuesta del endpoint GET /api/v1/personas/buscar.
 * Incluye datos de autocomplete + alertas de la central de riesgo interna.
 */
public record PersonaResumenResponse(
        // ── Datos de autocomplete ─────────────────────────────────────────
        String documentType,
        String documentNumber,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String email,
        String telefono1,
        String telefono2,
        String estadoCivil,
        String fechaNacimiento,
        String departamento,
        String provincia,
        String distrito,
        String direccion,
        String ocupacion,
        String rangoIngresos,
        String tipoVivienda,
        String licenciaConducir,
        String numeroLicencia,
        // ── Central de riesgo ─────────────────────────────────────────────
        /** true si hay al menos una alerta BLOQUEANTE. El frontend debe impedír el avance. */
        boolean tieneBloqueante,
        List<AlertaDto> alertas
) {

    public record AlertaDto(
            String nivel,
            String tipo,
            String descripcion,
            String codigoSolicitudRelacionada,
            String estadoSolicitudRelacionada,
            String motivoRechazo
    ) {}

    public static PersonaResumenResponse from(PersonaResumen dominio) {
        List<AlertaDto> alertasDto = dominio.getAlertas().stream()
                .map(a -> new AlertaDto(
                        a.getNivel().name(),
                        a.getTipo().name(),
                        a.getDescripcion(),
                        a.getCodigoSolicitudRelacionada(),
                        a.getEstadoSolicitudRelacionada(),
                        a.getMotivoRechazo()))
                .toList();

        boolean bloqueante = dominio.getAlertas().stream()
                .anyMatch(a -> a.getNivel() == AlertaCredito.Nivel.BLOQUEANTE);

        return new PersonaResumenResponse(
                dominio.getDocumentType(),
                dominio.getDocumentNumber(),
                dominio.getNombres(),
                dominio.getApellidoPaterno(),
                dominio.getApellidoMaterno(),
                dominio.getEmail(),
                dominio.getTelefono1(),
                dominio.getTelefono2(),
                dominio.getEstadoCivil(),
                dominio.getFechaNacimiento(),
                dominio.getDepartamento(),
                dominio.getProvincia(),
                dominio.getDistrito(),
                dominio.getDireccion(),
                dominio.getOcupacion(),
                dominio.getRangoIngresos(),
                dominio.getTipoVivienda(),
                dominio.getLicenciaConducir(),
                dominio.getNumeroLicencia(),
                bloqueante,
                alertasDto
        );
    }
}
