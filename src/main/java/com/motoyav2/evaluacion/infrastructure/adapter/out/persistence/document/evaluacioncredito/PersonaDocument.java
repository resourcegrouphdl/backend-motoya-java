package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PersonaDocument {

  private String id;
  private String tipo;

  // datos personales
  private String nombres;
  private String apellidoPaterno;
  private String apellidoMaterno;
  private String nombreCompleto;

  // documento de identidad
  private String tipoDeDocumento;
  private String numeroDeDocumento;
  private String nacionalidad;

  // datos demograficos
  private String sexo;
  private String fechaNacimiento;
  private String edad;
  private String estadoCivil;
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

  // datos laborales
  private String ocupacion;
  private String tipoTrabajo;
  private String nombreEmpresa;
  private String direccionDelTrabajo;
  private String ubicacionDelTrabajoLat;
  private String ubicacionDelTrabajoLng;
  private String antiguedadDelTrabajo;
  private String ingresoMensual;
  private String rangoIngresos;

  // licencia de conducir
  private String licenciaDeConducir;
  private String numeroDeLicencia;
  private String vencimientoLicencia;
  private String licenciaVigente;

  // documentos embebidos
  private List<DocumentoDocument> documentos;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
