package com.motoyav2.riesgointerno.domain.model;

import com.google.cloud.Timestamp;
import com.motoyav2.riesgointerno.domain.enums.EstadoRegistro;
import com.motoyav2.riesgointerno.domain.enums.NivelRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoSujeto;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class RegistroRiesgo {
    String id;
    String dniRegistrado;
    String nombreRegistrado;
    /** Array de teléfonos — permite array-contains queries en Firestore. */
    List<String> telefonos;
    TipoSujeto tipoSujeto;
    NivelRiesgo nivelRiesgo;
    EstadoRegistro estadoRegistro;
    TipoRiesgo tipoRiesgo;
    String contratoIdRelacionado;
    String solicitudIdRelacionado;
    Double montoDeudaPendiente;
    Timestamp fechaIncidente;
    String descripcion;
    List<String> evidencias;
    List<String> condicionesRehabilitacion;
    String registradoPor;
    List<HistorialCambioRiesgo> historialCambios;
    Timestamp fechaRegistro;
    Timestamp updatedAt;
}
