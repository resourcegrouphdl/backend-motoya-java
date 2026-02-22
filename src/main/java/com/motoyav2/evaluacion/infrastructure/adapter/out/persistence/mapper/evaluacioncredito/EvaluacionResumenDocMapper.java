package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.evaluacioncredito;

import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EvaluacionCreditoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.FinanciamientoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.PersonaDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.VehiculoDocument;

import java.math.BigDecimal;

public final class EvaluacionResumenDocMapper {

    private EvaluacionResumenDocMapper() {}

    public static EvaluacionResumen toDomain(EvaluacionCreditoDocument doc) {
        PersonaDocument titular = doc.getTitular();
        VehiculoDocument vehiculo = doc.getVehiculo();
        FinanciamientoDocument financiamiento = doc.getFinanciamiento();

        return EvaluacionResumen.builder()
                .id(doc.getCodigoDeSolicitud())
                .numeroEvaluacion(doc.getNumeroEvaluacion())
                .codigoSolicitud(doc.getCodigoDeSolicitud())
                .estado(doc.getEstado())
                .etapa(doc.getEtapa())
                .prioridad(doc.getPrioridad())
                .progreso(doc.getProgresoPorcentaje())
                .scoreFinal(doc.getScoreFinal())
                .nombreTitular(titular != null ? titular.getNombreCompleto() : null)
                .documentoTitular(titular != null ? titular.getNumeroDeDocumento() : null)
                .telefonoTitular(titular != null ? titular.getTelefono1() : null)
                .vehiculoDescripcion(vehiculo != null ? vehiculo.getDescripcionCompleta() : null)
                .montoVehiculo(toBigDecimal(financiamiento != null ? financiamiento.getMontoDelVehiculo() : null))
                .montoFinanciar(toBigDecimal(financiamiento != null ? financiamiento.getMontoAFinanciarOriginal() : null))
                .asignadoA(doc.getAsignadoA())
                .nombreEvaluador(doc.getNombreEvaluador())
                .tiendaId(doc.getTiendaId())
                .tiendaNombre(doc.getTiendaNombre())
                .alertasCount(doc.getAlertas() != null ? doc.getAlertas().size() : 0)
                .tieneFiador(doc.getFiador() != null && doc.getFiador().getId() != null)
                .creadoEn(doc.getCreadoEn())
                .actualizadoEn(doc.getActualizadoEn())
                .build();
    }

    private static BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
