package com.motoyav2.evaluacion.domain.enums;

import java.util.Arrays;

public enum EstadoSolicitud {
    PENDIENTE("pendiente"),
    EN_REVISION_INICIAL("en_revision_inicial"),
    EVALUACION_DOCUMENTAL("evaluacion_documental"),
    DOCUMENTOS_OBSERVADOS("documentos_observados"),
    DOCUMENTOS_COMPLETOS("documentos_completos"),
    DOCUMENTOS_INCOMPLETOS("documentos_incompletos"),
    CLIENTE_APROBADO("cliente_aprobado"),
    CLIENTE_RECHAZADO("cliente_rechazado"),
    EVALUACION_GARANTES("evaluacion_garantes"),
    FIADOR_APROBADO("fiador_aprobado"),
    FIADOR_RECHAZADO("fiador_rechazado"),
    REFERENCIAS_APROBADAS("referencias_aprobadas"),
    REFERENCIAS_RECHAZADAS("referencias_rechazadas"),
    VEHICULO_APROBADO("vehiculo_aprobado"),
    VEHICULO_RECHAZADO("vehiculo_rechazado"),
    DATOS_VERIFICADOS("datos_verificados"),
    DATOS_NO_VERIFICADOS("datos_no_verificados"),
    ENTREVISTA_PROGRAMADA("entrevista_programada"),
    ENTREVISTA_EN_CURSO("entrevista_en_curso"),
    ENTREVISTA_COMPLETADA("entrevista_completada"),
    EN_REVISION_FINAL("en_revision_final"),
    APROBADO("aprobado"),
    RECHAZADO("rechazado"),
    CONDICIONAL("condicional"),
    CERTIFICADO_GENERADO("certificado_generado"),
    ESPERANDO_INICIAL("esperando_inicial"),
    CONTRATO_GENERADO("contrato_generado"),
    CONTRATO_FIRMADO("contrato_firmado"),
    ENTREGA_COMPLETADA("entrega_completada"),
    CANCELADO("cancelado");

    private final String firestoreValue;

    EstadoSolicitud(String firestoreValue) {
        this.firestoreValue = firestoreValue;
    }

    public String getFirestoreValue() {
        return firestoreValue;
    }

    public static EstadoSolicitud fromFirestoreValue(String value) {
        if (value == null) return PENDIENTE;
        return Arrays.stream(values())
                .filter(e -> e.firestoreValue.equals(value))
                .findFirst()
                .orElse(PENDIENTE);
    }
}
