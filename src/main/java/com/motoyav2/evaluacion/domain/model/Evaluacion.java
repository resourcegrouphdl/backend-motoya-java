package com.motoyav2.evaluacion.domain.model;

import com.motoyav2.evaluacion.domain.enums.EstadoDeLaEtapa;
import com.motoyav2.evaluacion.domain.enums.PrioridadDeatencion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class Evaluacion {

    private String id;
    private String numeroEvaluacion;
    private String codigoDeSolicitud;
    private String solicitudFirebaseId;
    private String mensajeOpcional;

    // estado y progreso
    private String estado;
    private EstadoDeLaEtapa etapa;
    private PrioridadDeatencion prioridad;
    private Integer progresoPorcentaje;

    // relaciones a personas
    private String titularId;
    private String fiadorId;

    // relacion a vehiculo
    private String vehiculoId;

    // relacion a financiamiento
    private String financiamientoId;

    // asignacion
    private String asignadoA;
    private String nombreEvaluador;

    // tienda y vendedor
    private String tiendaId;
    private String tiendaNombre;
    private String tiendaCodigo;
    private String vendedorId;
    private String vendedorNombre;
    private String vendedorTelefono;
    private String vendedorEmail;

    // scores
    private String scoreDocumental;
    private String scoreReferencias;
    private String scoreCrediticio;
    private String scoreIngresos;
    private String scoreEntrevistaTitular;
    private String scoreEntrevistaFiador;
    private String scoreFinal;

    // decision final
    private String decision;
    private String decisionMotivo;
    private String decisionCondiciones;
    private String decisionFecha;
    private String decisionPor;
    private String decisionNombre;

    // montos aprobados
    private String inicialAprobada;
    private String montoFinanciarAprobado;
    private String cuotaAprobada;

    // control de versiones (optimistic locking)
    private Integer version;

    // auditoria
    private String creadoEn;
    private String actualizadoEn;
    private String creadoPor;
    private String actualizadoPor;

    public Evaluacion enriquecerConContextoComercial(VendedorInfo vendedor, TiendaInfo tienda) {
        return this.toBuilder()
                .vendedorNombre(vendedor.nombre() != null ? vendedor.nombre() : this.vendedorNombre)
                .vendedorTelefono(vendedor.telefono())
                .vendedorEmail(vendedor.email())
                .tiendaNombre(tienda.nombre() != null ? tienda.nombre() : this.tiendaNombre)
                .tiendaCodigo(tienda.codigo())
                .actualizadoEn(Instant.now().toString())
                .build();
    }

    public boolean tieneFiador() {
        return fiadorId != null && !fiadorId.isBlank();
    }
}
