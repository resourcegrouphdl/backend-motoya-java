package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.evaluacioncredito;

import com.motoyav2.evaluacion.domain.model.Alertas;
import com.motoyav2.evaluacion.domain.model.Documentos;
import com.motoyav2.evaluacion.domain.model.Entrevista;
import com.motoyav2.evaluacion.domain.model.EtapaDeEvaluacion;
import com.motoyav2.evaluacion.domain.model.Evaluacion;
import com.motoyav2.evaluacion.domain.model.Eventos;
import com.motoyav2.evaluacion.domain.model.Financiamiento;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.AlertaDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.DocumentoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EntrevistaDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EtapaDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EvaluacionCreditoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EventoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.FinanciamientoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.PersonaDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.ReferenciaDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.VehiculoDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring")
public interface EvaluacionCreditoMapper {

  // ═══════════════════════════════════════════════════════════
  // PERSONA ↔ PersonaDocument
  // ═══════════════════════════════════════════════════════════

  PersonaDocument toPersonaDocument(Persona persona);

  Persona toPersonaDomain(PersonaDocument document);

  // ═══════════════════════════════════════════════════════════
  // VEHICULO ↔ VehiculoDocument
  // ═══════════════════════════════════════════════════════════

  VehiculoDocument toVehiculoDocument(Vehiculo vehiculo);

  Vehiculo toVehiculoDomain(VehiculoDocument document);

  // ═══════════════════════════════════════════════════════════
  // FINANCIAMIENTO ↔ FinanciamientoDocument
  // ═══════════════════════════════════════════════════════════

  FinanciamientoDocument toFinanciamientoDocument(Financiamiento financiamiento);

  Financiamiento toFinanciamientoDomain(FinanciamientoDocument document);

  // ═══════════════════════════════════════════════════════════
  // DOCUMENTOS ↔ DocumentoDocument
  // ═══════════════════════════════════════════════════════════

  DocumentoDocument toDocumentoDocument(Documentos documento);

  Documentos toDocumentoDomain(DocumentoDocument document);

  List<DocumentoDocument> toDocumentoDocumentList(List<Documentos> documentos);

  List<Documentos> toDocumentoDomainList(List<DocumentoDocument> documents);

  // ═══════════════════════════════════════════════════════════
  // REFERENCIAS ↔ ReferenciaDocument
  // ═══════════════════════════════════════════════════════════

  @Mapping(target = "resultadoDeVerificacion", source = "resultadoDeVerificacion", qualifiedByName = "enumToString")
  @Mapping(target = "fechaDeVerificacion", source = "fechaDeVerificacion", qualifiedByName = "instantToString")
  ReferenciaDocument toReferenciaDocument(ReferenciasDelTitular referencia);

  @Mapping(target = "resultadoDeVerificacion", source = "resultadoDeVerificacion", qualifiedByName = "stringToResultadoVerificacion")
  @Mapping(target = "fechaDeVerificacion", source = "fechaDeVerificacion", qualifiedByName = "stringToInstant")
  ReferenciasDelTitular toReferenciaDomain(ReferenciaDocument document);

  List<ReferenciaDocument> toReferenciaDocumentList(List<ReferenciasDelTitular> referencias);

  List<ReferenciasDelTitular> toReferenciaDomainList(List<ReferenciaDocument> documents);

  // ═══════════════════════════════════════════════════════════
  // ENTREVISTA ↔ EntrevistaDocument
  // ═══════════════════════════════════════════════════════════

  @Mapping(target = "gastosMensuales", source = "gastosMensuales", qualifiedByName = "bigDecimalToString")
  @Mapping(target = "capacidadPagoCalculada", source = "capacidadPagoCalculada", qualifiedByName = "bigDecimalToString")
  @Mapping(target = "scoreTotal", source = "scoreTotal", qualifiedByName = "bigDecimalToString")
  @Mapping(target = "fechaEntrevista", source = "fechaEntrevista", qualifiedByName = "instantToString")
  EntrevistaDocument toEntrevistaDocument(Entrevista entrevista);

  @Mapping(target = "gastosMensuales", source = "gastosMensuales", qualifiedByName = "stringToBigDecimal")
  @Mapping(target = "capacidadPagoCalculada", source = "capacidadPagoCalculada", qualifiedByName = "stringToBigDecimal")
  @Mapping(target = "scoreTotal", source = "scoreTotal", qualifiedByName = "stringToBigDecimal")
  @Mapping(target = "fechaEntrevista", source = "fechaEntrevista", qualifiedByName = "stringToInstant")
  Entrevista toEntrevistaDomain(EntrevistaDocument document);

  List<EntrevistaDocument> toEntrevistaDocumentList(List<Entrevista> entrevistas);

  List<Entrevista> toEntrevistaDomainList(List<EntrevistaDocument> documents);

  // ═══════════════════════════════════════════════════════════
  // ALERTAS ↔ AlertaDocument
  // ═══════════════════════════════════════════════════════════

  @Mapping(target = "severidad", source = "severidad", qualifiedByName = "enumToString")
  @Mapping(target = "fechaDeResolucion", source = "fechaDeResolucion", qualifiedByName = "instantToString")
  AlertaDocument toAlertaDocument(Alertas alerta);

  @Mapping(target = "severidad", source = "severidad", qualifiedByName = "stringToSeveridad")
  @Mapping(target = "fechaDeResolucion", source = "fechaDeResolucion", qualifiedByName = "stringToInstant")
  Alertas toAlertaDomain(AlertaDocument document);

  List<AlertaDocument> toAlertaDocumentList(List<Alertas> alertas);

  List<Alertas> toAlertaDomainList(List<AlertaDocument> documents);

  // ═══════════════════════════════════════════════════════════
  // ETAPA DE EVALUACION ↔ EtapaDocument
  // ═══════════════════════════════════════════════════════════

  @Mapping(target = "estado", source = "estado", qualifiedByName = "enumToString")
  EtapaDocument toEtapaDocument(EtapaDeEvaluacion etapa);

  @Mapping(target = "estado", source = "estado", qualifiedByName = "stringToEstadoDeLaEtapa")
  EtapaDeEvaluacion toEtapaDomain(EtapaDocument document);

  List<EtapaDocument> toEtapaDocumentList(List<EtapaDeEvaluacion> etapas);

  List<EtapaDeEvaluacion> toEtapaDomainList(List<EtapaDocument> documents);

  // ═══════════════════════════════════════════════════════════
  // EVENTOS ↔ EventoDocument
  // ═══════════════════════════════════════════════════════════

  EventoDocument toEventoDocument(Eventos evento);

  Eventos toEventoDomain(EventoDocument document);

  List<EventoDocument> toEventoDocumentList(List<Eventos> eventos);

  List<Eventos> toEventoDomainList(List<EventoDocument> documents);

  // ═══════════════════════════════════════════════════════════
  // EVALUACION (core) ↔ EvaluacionCreditoDocument (core fields)
  // ═══════════════════════════════════════════════════════════

  @Mapping(target = "titular", ignore = true)
  @Mapping(target = "fiador", ignore = true)
  @Mapping(target = "vehiculo", ignore = true)
  @Mapping(target = "financiamiento", ignore = true)
  @Mapping(target = "documentos", ignore = true)
  @Mapping(target = "referencias", ignore = true)
  @Mapping(target = "entrevistas", ignore = true)
  @Mapping(target = "alertas", ignore = true)
  @Mapping(target = "etapas", ignore = true)
  @Mapping(target = "eventos", ignore = true)
  @Mapping(target = "etapa", source = "etapa", qualifiedByName = "enumToString")
  @Mapping(target = "prioridad", source = "prioridad", qualifiedByName = "enumToString")
  EvaluacionCreditoDocument evaluacionToDocument(Evaluacion evaluacion);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "titularId", ignore = true)
  @Mapping(target = "fiadorId", ignore = true)
  @Mapping(target = "vehiculoId", ignore = true)
  @Mapping(target = "financiamientoId", ignore = true)
  @Mapping(target = "etapa", source = "etapa", qualifiedByName = "stringToEstadoDeLaEtapa")
  @Mapping(target = "prioridad", source = "prioridad", qualifiedByName = "stringToPrioridad")
  Evaluacion documentToEvaluacion(EvaluacionCreditoDocument document);

  // ═══════════════════════════════════════════════════════════
  // METODO DE ENSAMBLAJE: Dominio completo → Documento Firestore
  // ═══════════════════════════════════════════════════════════

  default EvaluacionCreditoDocument toFullDocument(
      Evaluacion evaluacion,
      Persona titular,
      Persona fiador,
      Vehiculo vehiculo,
      Financiamiento financiamiento,
      List<Documentos> documentos,
      List<ReferenciasDelTitular> referencias,
      List<Entrevista> entrevistas,
      List<Alertas> alertas,
      List<EtapaDeEvaluacion> etapas,
      List<Eventos> eventos) {

    EvaluacionCreditoDocument doc = evaluacionToDocument(evaluacion);
    doc.setTitular(toPersonaDocument(titular));
    if (fiador != null) {
      doc.setFiador(toPersonaDocument(fiador));
    }
    doc.setVehiculo(toVehiculoDocument(vehiculo));
    doc.setFinanciamiento(toFinanciamientoDocument(financiamiento));
    doc.setDocumentos(documentos != null ? toDocumentoDocumentList(documentos) : List.of());
    doc.setReferencias(referencias != null ? toReferenciaDocumentList(referencias) : List.of());
    doc.setEntrevistas(entrevistas != null ? toEntrevistaDocumentList(entrevistas) : List.of());
    doc.setAlertas(alertas != null ? toAlertaDocumentList(alertas) : List.of());
    doc.setEtapas(etapas != null ? toEtapaDocumentList(etapas) : List.of());
    doc.setEventos(eventos != null ? toEventoDocumentList(eventos) : List.of());
    return doc;
  }

  // ═══════════════════════════════════════════════════════════
  // CONVERSIONES DE TIPO (helpers para MapStruct)
  // ═══════════════════════════════════════════════════════════

  @Named("enumToString")
  default String enumToString(Enum<?> value) {
    return value != null ? value.name() : null;
  }

  @Named("instantToString")
  default String instantToString(Instant value) {
    return value != null ? value.toString() : null;
  }

  @Named("stringToInstant")
  default Instant stringToInstant(String value) {
    return value != null ? Instant.parse(value) : null;
  }

  @Named("bigDecimalToString")
  default String bigDecimalToString(BigDecimal value) {
    return value != null ? value.toPlainString() : null;
  }

  @Named("stringToBigDecimal")
  default BigDecimal stringToBigDecimal(String value) {
    return value != null ? new BigDecimal(value) : null;
  }

  @Named("stringToResultadoVerificacion")
  default com.motoyav2.evaluacion.domain.enums.ResultadoDeVerificacion stringToResultadoVerificacion(String value) {
    return value != null ? com.motoyav2.evaluacion.domain.enums.ResultadoDeVerificacion.valueOf(value) : null;
  }

  @Named("stringToSeveridad")
  default com.motoyav2.evaluacion.domain.enums.Severidad stringToSeveridad(String value) {
    return value != null ? com.motoyav2.evaluacion.domain.enums.Severidad.valueOf(value) : null;
  }

  @Named("stringToEstadoDeLaEtapa")
  default com.motoyav2.evaluacion.domain.enums.EstadoDeLaEtapa stringToEstadoDeLaEtapa(String value) {
    return value != null ? com.motoyav2.evaluacion.domain.enums.EstadoDeLaEtapa.valueOf(value) : null;
  }

  @Named("stringToPrioridad")
  default com.motoyav2.evaluacion.domain.enums.PrioridadDeatencion stringToPrioridad(String value) {
    return value != null ? com.motoyav2.evaluacion.domain.enums.PrioridadDeatencion.valueOf(value) : null;
  }

}
