package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "evaluacionDeCredito")
public class EvaluacionCreditoDocument {

  @DocumentId
  private String codigoDeSolicitud;

  // identificadores
  private String numeroEvaluacion;
  private String solicitudFirebaseId;
  private String mensajeOpcional;

  // estado y progreso
  private String estado;
  private String etapa;
  private String prioridad;
  private Integer progresoPorcentaje;

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

  // control de versiones
  private Integer version;

  // entidades embebidas 1:1
  private PersonaDocument titular;
  private PersonaDocument fiador;
  private VehiculoDocument vehiculo;
  private FinanciamientoDocument financiamiento;

  // listas embebidas 1:N
  private List<DocumentoDocument> documentos;
  private List<ReferenciaDocument> referencias;
  private List<EntrevistaDocument> entrevistas;
  private List<AlertaDocument> alertas;
  private List<EtapaDocument> etapas;
  private List<EventoDocument> eventos;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;
  private String creadoPor;
  private String actualizadoPor;

}
