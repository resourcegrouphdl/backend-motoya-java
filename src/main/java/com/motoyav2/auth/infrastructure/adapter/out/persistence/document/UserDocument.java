package com.motoyav2.auth.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;

@Data
@Document(collectionName = "users")
public class UserDocument {

    // uid se almacena como campo de datos Y como ID del documento (compatibilidad con app legada).
    // No usar @DocumentId — ese conflicto rompe la deserialización en colecciones con uid como campo.
    // Las escrituras se hacen vía SDK directo de Firestore (firestore.collection(...).document(uid).set/update).
    private String uid;
    private String authUID;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String userType;
    private String userCategory;
    private String documentType;
    private String documentNumber;
    private String password;
    private String processingStatus;
    private String createdBy;
    private Boolean isActive;
    private Boolean isFirstLogin;
    private Boolean emailSent;
    private List<String> storeIds;
    private List<String> modulos;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp emailSentAt;
    private Timestamp lastPasswordChange;
}
