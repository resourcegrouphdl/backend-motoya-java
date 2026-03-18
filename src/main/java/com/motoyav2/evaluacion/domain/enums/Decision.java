package com.motoyav2.evaluacion.domain.enums;

public enum Decision {
    APROBADO("aprobado"),
    RECHAZADO("rechazado"),
    CONDICIONAL("condicional"),
    EN_REVISION_FINAL("en_revision_final");

    private final String firestoreValue;

    Decision(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    public String getFirestoreValue() {
        return firestoreValue;
    }

    public static Decision fromValue(String value) {
        if (value == null) return null;
        for (Decision d : values()) {
            if (d.firestoreValue.equalsIgnoreCase(value)) return d;
        }
        return null;
    }
}
