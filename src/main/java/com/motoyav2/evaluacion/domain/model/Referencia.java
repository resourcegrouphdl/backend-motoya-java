package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Referencia {
    String id;
    Integer numero;         // 1 | 2 | 3
    String nombre;
    String apellidos;
    String telefono;
    String parentesco;
    String titularId;
    String estadoVerificacion;  // pendiente | contactado | verificado | no_contactado | rechazado
    String resultadoContacto;   // positivo - ok | no contesta | negativo
    Integer scoreVerificacion;
    String actitudDuranteContacto;
    String observaciones;
    Timestamp fechaContacto;
    Boolean rechazada;
    Timestamp fechaRechazo;
    Timestamp createdAt;
    Timestamp updatedAt;

    public boolean estaVerificada() {
        return "verificado".equals(estadoVerificacion);
    }
}
