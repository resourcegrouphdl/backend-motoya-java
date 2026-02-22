package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

@Data
@Document(collectionName = "vehiculos")
public class FirebaseVehiculo {
  private String id;
  private String anio;
  private String codigoDeSolicitud;
  private String color;
  private Timestamp createdAt;
  private String marca;
  private String modelo;
  private Timestamp updatedAt;
}
