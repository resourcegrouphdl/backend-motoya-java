package com.motoyav2.auth.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;

@Data
@Document(collectionName = "users")
public class UserDocument {

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
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp emailSentAt;
    private Timestamp lastPasswordChange;
}
