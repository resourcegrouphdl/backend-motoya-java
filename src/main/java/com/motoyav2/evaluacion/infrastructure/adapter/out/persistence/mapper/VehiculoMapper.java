package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.motoyav2.evaluacion.domain.model.Vehiculo;

import java.util.Map;

public final class VehiculoMapper {

    private VehiculoMapper() {}

    public static Vehiculo toDomain(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Map<String, Object> data = doc.getData();
        if (data == null) return null;

        return Vehiculo.builder()
                .id(doc.getId())
                .marca(str(data, "marca"))
                .modelo(str(data, "modelo"))
                .anio(str(data, "anio"))   // ⚠️ string en Firestore
                .color(str(data, "color"))
                .precioReferencial(dbl(data, "precioReferencial"))
                .cilindrada(dbl(data, "cilindrada"))
                .createdAt(timestamp(data, "createdAt"))
                .updatedAt(timestamp(data, "updatedAt"))
                .build();
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static Double dbl(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    private static Timestamp timestamp(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp t) return t;
        return null;
    }
}
