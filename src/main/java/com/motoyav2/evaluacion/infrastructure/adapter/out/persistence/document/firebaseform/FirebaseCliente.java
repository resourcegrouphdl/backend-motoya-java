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
  private String fechaNacimiento;
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
  private String antiguedadDomiciliaria;
  private String referenciaUbicacion;
  private String ubicacionGpsLat;
  private String ubicacionGpsLng;
  private String ocupacion;
  private String tipoTrabajo;
  private String nombreEmpresa;
  private String direccionDelTrabajo;
  private String ubicacionDelTrabajoLat;
  private String ubicacionDelTrabajoLng;
  private String antiguedadDelTrabajo;
  private String ingresoMensual;
  private String rangoIngresos;
  private String licenciaDeConducir;
  private String numeroDeLicencia;
  private String vencimientoLicencia;
  private String licenciaVigente;
  private Map<String,String> archivos;
  private String codigoDeSolicitud;
  private Timestamp createdAt;
  private Timestamp updatedAt;
}
