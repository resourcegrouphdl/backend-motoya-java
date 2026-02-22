package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PersonaDto {

    private String id;
    private String nombres;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String nombreCompleto;
    private String sexo;
    private String fechaNacimiento;
    private Integer edad;
    private String estadoCivil;
    private Integer cargasFamiliares;
    private String documentType;
    private String documentNumber;
    private String nacionalidad;
    private String email;
    private String telefono1;
    private String telefono2;
    private String departamento;
    private String provincia;
    private String distrito;
    private String direccion;
    private String direccionCompleta;
    private String tipoVivienda;
    private String antiguedadDomiciliaria;
    private String referenciaUbicacion;
    private UbicacionGpsDto ubicacionGPS;
    private String ocupacion;
    private String tipoTrabajo;
    private String nombreEmpresa;
    private UbicacionGpsDto ubicacionGPSTrabajo;
    private String antiguedadTrabajo;
    private Double ingresoMensual;
    private String rangoIngresos;
    private String licenciaConducir;
    private String numeroLicencia;
    private String vencimientoLicencia;
    private Boolean licenciaVigente;
    private Map<String, String> archivos;
}
