package com.motoyav2.gestion.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;

/**
 * Documento Firestore para la colección tienda_profiles.
 * Extiende los 4 campos que ya usaba el backend con todos los campos
 * que maneja la app Electron.
 */
@Data
@Document(collectionName = "tienda_profiles")
public class TiendaProfileDocument {

    // uid se almacena como campo de datos Y como ID del documento (compatibilidad con app legada).
    // Las escrituras se hacen vía SDK directo de Firestore.
    private String uid;

    // Representante legal (mismo uid que users/{uid})
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String documentType;
    private String documentNumber;
    private String userType;        // siempre "tienda"
    private String userCategory;    // siempre "externo"

    // Datos del negocio
    private String businessName;
    private String taxId;           // RUC

    // Ubicación
    private String address;
    private String city;
    private String district;
    private String postalCode;
    private Double latitude;
    private Double longitude;

    // Comercial
    private String bankAccount;
    private String contactPersonName;
    private String contactPersonPhone;
    private String legalRepresentative;

    // Online
    private String website;
    private String facebook;
    private String instagram;
    private String whatsapp;
    private Object socialMedia;      // Electron app stored as nested object

    // Comercial adicional
    private Integer plazoFacturaDias;
    private Object coordinates;      // Electron app stored as nested {lat, lng}

    // Estado
    private String tiendaStatus;    // activa | suspendida | pendiente_aprobacion | rechazada
    private Boolean isActive;

    // Campos duplicados del user doc (Electron app los escribía en tienda_profiles también — ignorar)
    private String password;
    private Boolean isFirstLogin;
    private List<String> storeIds;
    private Timestamp lastPasswordChange;

    // Auditoría
    private String createdBy;
    private String notes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp contractStartDate;
    private Timestamp contractEndDate;
}
