package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class Cliente {
    String id;
    String tipo;            // titular | fiador
    String nombres;
    String apellidoPaterno;
    String apellidoMaterno;
    String sexo;
    String fechaNacimiento; // string "YYYY-MM-DD"
    String estadoCivil;
    Integer cargasFamiliares;
    String documentType;
    String documentNumber;
    String nacionalidad;
    String email;
    String telefono1;
    String telefono2;
    String departamento;
    String provincia;
    String distrito;
    String direccion;
    String ubicacionGPSCasa;
    String tipoVivienda;
    String detalleVivienda;
    String referenciaUbicacion;
    String licenciaConducir;
    String numeroLicencia;
    Boolean tienePapeletasPendientes;
    Double totalDeudaPapeletas;
    String ocupacion;
    String tipoTrabajo;
    String nombreTrabajoEmpresa;
    String ubicacionGPSTrabajo;
    String comoSustentaIngresos;
    Double ingresoMensual;
    String rangoIngresos;
    String perfilSentinel;
    Double totalDeudaBancos;
    Double totalOtrasDeudas;
    String tipoCliente;
    Map<String, String> archivos;
    Map<String, EvaluacionDocumento> evaluacionDocumentos;
    String estadoValidacionDocumentos;
    List<String> documentosObservados;
    Boolean datosVerificados;
    String observacionesEvaluador;
    EvaluacionEntrevista evaluacionEntrevista;
    /** Snapshot de la verificación de identidad — null si aún no se verificó. */
    VerificacionIdentidadSnapshot verificacionIdentidad;
    /** Resultado de la validación MX del email — null si aún no se validó. */
    ValidacionEmail validacionEmail;
    Timestamp createdAt;
    Timestamp updatedAt;
    Timestamp fechaValidacionDocumentos;

    public String getNombreCompleto() {
        return String.join(" ", nombres, apellidoPaterno, apellidoMaterno).trim();
    }

    public boolean entrevistaFinalizada() {
        return evaluacionEntrevista != null
                && Boolean.FALSE.equals(evaluacionEntrevista.getEsBorrador());
    }

    public boolean entrevistaAprobada() {
        return entrevistaFinalizada()
                && "aprobar".equals(evaluacionEntrevista.getRecomendacion());
    }
}
