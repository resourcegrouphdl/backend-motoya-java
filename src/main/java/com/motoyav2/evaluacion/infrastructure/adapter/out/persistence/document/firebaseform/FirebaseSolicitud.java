package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;

@Data
@Document(collectionName = "solicitudes")
public class FirebaseSolicitud {

  private String formularioId;
  private String codigoDeSolicitud;
  private Timestamp createdAt;
  private String titularId;
  private String fiadorId;
  private String mensajeOpcional;
  private List<String> referenciasIds;
  private String vehiculoId;
  private Long montoCuota;
  private Long plazoQuincenas;
  private Long precioCompraMoto;
  private VendedorFirebase vendedor;
}