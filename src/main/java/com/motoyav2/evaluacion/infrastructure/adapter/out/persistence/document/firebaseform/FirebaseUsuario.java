package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;

@Data
@Document(collectionName = "usuarios")
public class FirebaseUsuario {
    private String id;
    private String nombre;
    private String email;
    private String rol;          // admin | supervisor | asesor | evaluador | vendedor
    private List<String> roles;  // algunos docs usan lista
    private String tiendaId;
    private Boolean activo;
    private Timestamp createdAt;
}
