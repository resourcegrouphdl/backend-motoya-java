package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.Map;

@Data
@Document(collectionName = "referencias")
public class FirebaseReferencias {
  private String apellidos;
  private String codigoDeSolicitud;
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private String nombre;
  private String parentesco;
  private String telefono;
  private String titularId;

  // verificación
  private Integer numero;             // 1 | 2 | 3
  private String estadoVerificacion;  // 'pendiente'|'contactado'|'verificado'|'no_contactado'|'rechazado'
  private String resultadoContacto;   // 'positivo - ok'|'no contesta'|'negativo'
  private Double scoreVerificacion;
  private Double scoreMaximo;
  private String calificacion;
  private String actitudDuranteContacto;
  private String observaciones;
  private Timestamp fechaContacto;
  private Boolean rechazada;
  private Timestamp fechaRechazo;
  private Map<String, Object> respuestasPreguntas;
}
