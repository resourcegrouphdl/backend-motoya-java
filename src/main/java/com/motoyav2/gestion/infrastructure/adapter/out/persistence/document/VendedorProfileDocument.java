package com.motoyav2.gestion.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

/**
 * Documento Firestore para la colección vendedor_profiles.
 */
@Data
@Document(collectionName = "vendedor_profiles")
public class VendedorProfileDocument {

    // uid se almacena como campo de datos Y como ID del documento (compatibilidad con app legada).
    // Las escrituras se hacen vía SDK directo de Firestore.
    private String uid;

    // Datos personales (mismo uid que users/{uid})
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String documentType;
    private String documentNumber;
    private String userType;        // siempre "vendedor"
    private String userCategory;    // siempre "externo"

    // Relación con tienda (obligatorio)
    private String tiendaId;
    private String employeeId;
    private String position;        // cargo: Vendedor, Vendedor Senior, Supervisor, etc.
    private String supervisorId;

    // Estado laboral
    private String vendedorStatus;  // activo | inactivo | suspendido
    private Boolean isActive;

    // Configuración laboral
    private Double commissionRate;  // 0-50%
    private Double salesGoal;       // meta mensual en soles
    private Integer experience;     // años de experiencia

    // Contacto de emergencia — Electron escribe objeto anidado {name, phone, relationship}
    private Object emergencyContact;
    // Campos legacy (app Electron usa objeto anidado, no campos separados — mantener para compatibilidad)
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelationship;

    // Datos adicionales
    private String address;
    private String city;
    private String district;
    private String gender;          // masculino | femenino
    private String education;
    private String notes;

    // Auditoría
    private String createdBy;
    private Timestamp hireDate;
    private Object birthDate;   // Mixed: some docs have String (Electron legacy), others Timestamp
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
