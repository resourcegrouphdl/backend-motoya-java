package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

@Data
@Document(collectionName = "referencias")
public class FirebaseReferencias {
  private String apellidos;
  private String codigoDeSolicitud;
  private Timestamp createdAt;
  private String nombre;
  private String parentesco;
  private String telefono;
  private String titularId;
  private Timestamp updatedAt;
}
