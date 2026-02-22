package com.motoyav2.evaluacion.infrastructure.adapter.in.web.mapper;

import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.EvaluacionResumenDto;

public final class EvaluacionResumenDtoMapper {

    private EvaluacionResumenDtoMapper() {}

    public static EvaluacionResumenDto toDto(EvaluacionResumen resumen) {
        return EvaluacionResumenDto.builder()
                .id(resumen.getId())
                .numeroEvaluacion(resumen.getNumeroEvaluacion())
                .codigoSolicitud(resumen.getCodigoSolicitud())
                .estado(resumen.getEstado())
                .etapa(resumen.getEtapa())
                .prioridad(resumen.getPrioridad())
                .progreso(resumen.getProgreso())
                .scoreFinal(resumen.getScoreFinal())
                .nombreTitular(resumen.getNombreTitular())
                .documentoTitular(resumen.getDocumentoTitular())
                .telefonoTitular(resumen.getTelefonoTitular())
                .vehiculoDescripcion(resumen.getVehiculoDescripcion())
                .montoVehiculo(resumen.getMontoVehiculo())
                .montoFinanciar(resumen.getMontoFinanciar())
                .asignadoA(resumen.getAsignadoA())
                .nombreEvaluador(resumen.getNombreEvaluador())
                .tiendaId(resumen.getTiendaId())
                .tiendaNombre(resumen.getTiendaNombre())
                .alertasCount(resumen.getAlertasCount())
                .tieneFiador(resumen.isTieneFiador())
                .creadoEn(resumen.getCreadoEn())
                .actualizadoEn(resumen.getActualizadoEn())
                .build();
    }
}
