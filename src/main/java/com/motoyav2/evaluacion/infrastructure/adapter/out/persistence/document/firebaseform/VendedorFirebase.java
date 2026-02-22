package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VendedorFirebase {
  private String id;
  private String nombreVendedor;
  private List<String> tienda;
}
