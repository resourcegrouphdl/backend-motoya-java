package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Document(collectionName = "clientes_v1")
public class FirebaseCliente {

  private String id;
  private String tipo;
  private String nombres;
  private String apellidoPaterno;
  private String apellidoMaterno;
  private String documentType;
  private String documentNumber;
  private String nacionalidad;
  private String sexo;
  private String fechaNacimiento;  // "YYYY-MM-DD" string, NOT Timestamp
  private String edad;
  private String estadoCivil;
  private String cargasFamiliares;
  private String email;
  private String telefono1;
  private String telefono2;
  private String departamento;
  private String provincia;
  private String distrito;
  private String direccion;
  private String tipoDeVivienda;
  private String tipoVivienda;     // alias — Firestore may use either name
  private String estadoResidenciaCE;   // vigente | vencido (para CE / Pasaporte)
  private String estadoCarnetPlastico; // vigente | vencido
  private String antiguedadDomiciliaria;
  private String referenciaUbicacion;
  private String ubicacionGpsLat;
  private String ubicacionGpsLng;
  private String ubicacionGPSCasa; // URL format used in newer records
  private String ocupacion;
  private String tipoTrabajo;
  private String nombreEmpresa;
  private String nombreTrabajoEmpresa; // alias used in newer records
  private String direccionDelTrabajo;
  private String ubicacionDelTrabajoLat;
  private String ubicacionDelTrabajoLng;
  private String antiguedadDelTrabajo;
  private String ingresoMensual;
  private String rangoIngresos;       // "1500-2000" — parsed in scoring service
  private String frecuenciaIngresos;
  private String licenciaDeConducir;
  private String licenciaConducir;    // alias — Firestore may use either name
  private String numeroDeLicencia;
  private String numeroLicencia;      // alias
  private String vencimientoLicencia;
  private String licenciaVigente;
  private Boolean reflejaLicenciaWebMTC;
  private Boolean tienePapeletasPendientes;
  private Double totalDeudaPapeletas;

  // perfil financiero
  private String perfilSentinel;      // 'verde - paga puntual' | 'amarillo - atraso moderado' | 'rojo - no paga'
  private Double totalDeudaBancos;
  private Double totalOtrasDeudas;

  // estado de documentacion
  private String estadoValidacionDocumentos;  // 'pendiente'|'aprobado'|'observado'|'rechazado'
  private List<String> documentosObservados;
  private Boolean datosVerificados;
  private String observacionesEvaluador;

  // evaluación de documentos: Map<TipoDocumento, {estado, observaciones, fechaEvaluacion, evaluador}>
  private Map<String, Object> evaluacionDocumentos;

  // evaluación de entrevista (embebida — NO es sub-colección)
  private Map<String, Object> evaluacionEntrevista;

  private Map<String, String> archivos;
  private String codigoDeSolicitud;
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private Timestamp fechaValidacionDocumentos;
}
