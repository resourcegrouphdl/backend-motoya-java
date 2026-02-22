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
@Document(collectionName = "tienda_profiles")
public class TiendaProfileDocument {

  private String uid;
  private String businessName;
  private String email;
  private String phone;
  private String address;
  private String city;
  private String district;
  private String taxId;
  private Boolean isActive;
  private String tiendaStatus;
  private String contactPersonName;
  private String contactPersonPhone;

}
