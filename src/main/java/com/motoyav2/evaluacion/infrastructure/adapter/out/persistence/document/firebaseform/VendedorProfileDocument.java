package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "vendedor_profiles")
public class VendedorProfileDocument {

  private String uid;
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String documentNumber;
  private String position;
  private String tiendaId;
  private Boolean isActive;
  private String vendedorStatus;

}
