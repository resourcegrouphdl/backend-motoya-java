package com.motoyav2.evaluacion.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Persona {

  private String id;
  private String tipo;              // 'titular' | 'fiador'

  // datos personales
  private String nombres;
  private String apellidoPaterno;
  private String apellidoMaterno;
  private String nombreCompleto;

  // documento de identidad
  private String tipoDeDocumento;
  private String numeroDeDocumento;
  private String nacionalidad;
  private String estadoResidenciaCE;    // vigente | vencido
  private String estadoCarnetPlastico;  // vigente | vencido

  // datos demograficos
  private String sexo;
  private String fechaNacimiento;       // "YYYY-MM-DD"
  private String edad;
  private String estadoCivil;
  private Integer cargasFamiliaresNum; // parseado desde cargasFamiliares string
  private String cargasFamiliares;

  // contacto
  private String email;
  private String telefono1;
  private String telefono2;

  // domicilio
  private String departamento;
  private String provincia;
  private String distrito;
  private String direccion;
  private String direccionCompleta;
  private String tipoDeVivienda;
  private String antiguedadDomiciliaria;
  private String referenciaUbicacion;
  private String ubicacionGpsLat;
  private String ubicacionGpsLng;
  private String ubicacionGPSCasa;

  // datos laborales
  private String ocupacion;
  private String tipoTrabajo;
  private String nombreEmpresa;
  private String direccionDelTrabajo;
  private String ubicacionDelTrabajoLat;
  private String ubicacionDelTrabajoLng;
  private String antiguedadDelTrabajo;
  private String ingresoMensual;
  private Double ingresoMensualNum;     // parseado para cálculos
  private String rangoIngresos;         // "1500-2000"
  private String frecuenciaIngresos;

  // licencia de conducir
  private String licenciaDeConducir;    // 'vigente' | 'vencida'
  private String numeroDeLicencia;
  private String vencimientoLicencia;
  private String licenciaVigente;
  private Boolean reflejaLicenciaWebMTC;
  private Boolean tienePapeletasPendientes;
  private Double totalDeudaPapeletas;

  // perfil financiero / sentinel
  private String perfilSentinel;        // 'verde - paga puntual' | 'amarillo - atraso moderado' | 'rojo - no paga'
  private Double totalDeudaBancos;
  private Double totalOtrasDeudas;

  // estado de validación de documentos
  private String estadoValidacionDocumentos;
  private List<String> documentosObservados;
  private Boolean datosVerificados;
  private String observacionesEvaluador;

  // evaluación de documentos: TipoDocumento → {estado, observaciones, fechaEvaluacion, evaluador}
  private Map<String, Map<String, Object>> evaluacionDocumentos;

  // entrevista de evaluación (embebida en clientes_v1, NO sub-colección)
  private EntrevistaCompleta evaluacionEntrevista;

  // archivos / documentos
  private List<Documentos> documentos;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;
  private String fechaValidacionDocumentos;

}
