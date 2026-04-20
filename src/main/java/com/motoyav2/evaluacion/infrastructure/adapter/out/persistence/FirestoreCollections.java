package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence;

/**
 * Centraliza los nombres de colecciones de Firestore usadas en el módulo evaluación.
 */
public final class FirestoreCollections {

    private FirestoreCollections() {}

    public static final String SOLICITUDES       = "solicitudes";
    public static final String CLIENTES          = "clientes_v1";
    public static final String VEHICULOS         = "vehiculos";
    public static final String REFERENCIAS       = "referencias";
    public static final String HISTORIAL_ESTADOS = "cambios_estado_solicitud";
    public static final String ALERTAS           = "alertas";
    public static final String USUARIOS          = "usuarios";
    /** Registro maestro de personas — solo para autocomplete y central de riesgo. */
    public static final String PERSONAS          = "personas";
    /** Historial de conversaciones WhatsApp por solicitud. */
    public static final String CONVERSACIONES_WA = "conversaciones_whatsapp";

    /*
    public static final String SOLICITUDES       = "solicitudes";
    public static final String CLIENTES          = "clientes_v1";
    public static final String VEHICULOS         = "vehiculos";
    public static final String REFERENCIAS       = "referencias";
    public static final String HISTORIAL_ESTADOS = "cambios_estado_solicitud";
    public static final String ALERTAS           = "alertas";
    public static final String USUARIOS          = "usuarios";

     */
}