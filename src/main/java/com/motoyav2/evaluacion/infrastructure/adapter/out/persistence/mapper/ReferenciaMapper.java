package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.motoyav2.evaluacion.domain.model.Referencia;

import java.util.Map;

public final class ReferenciaMapper {

    private ReferenciaMapper() {}

    public static Referencia toDomain(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Map<String, Object> data = doc.getData();
        if (data == null) return null;

        return Referencia.builder()
                .id(doc.getId())
                .numero(toInt(data.get("numero")))
                .nombre(str(data, "nombre"))
                .apellidos(str(data, "apellidos"))
                .telefono(str(data, "telefono"))
                .parentesco(str(data, "parentesco"))
                .titularId(str(data, "titularId"))
                .estadoVerificacion(str(data, "estadoVerificacion"))
                .resultadoContacto(str(data, "resultadoContacto"))
                .scoreVerificacion(toInt(data.get("scoreVerificacion")))
                .actitudDuranteContacto(str(data, "actitudDuranteContacto"))
                .observaciones(str(data, "observaciones"))
                .fechaContacto(timestamp(data, "fechaContacto"))
                .rechazada(bool(data, "rechazada"))
                .fechaRechazo(timestamp(data, "fechaRechazo"))
                .createdAt(timestamp(data, "createdAt"))
                .updatedAt(timestamp(data, "updatedAt"))
                .build();
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private static Boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return null;
    }

    private static Timestamp timestamp(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp t) return t;
        return null;
    }
}
