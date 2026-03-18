package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;

import java.util.Map;

public final class HistorialEstadoMapper {

    private HistorialEstadoMapper() {}

    public static HistorialEstado toDomain(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Map<String, Object> data = doc.getData();
        if (data == null) return null;

        return HistorialEstado.builder()
                .id(doc.getId())
                .solicitudId(str(data, "solicitudId"))
                .estadoAnterior(EstadoSolicitud.fromFirestoreValue(str(data, "estadoAnterior")))
                .estadoNuevo(EstadoSolicitud.fromFirestoreValue(str(data, "estadoNuevo")))
                .fechaCambio(timestamp(data, "fechaCambio"))
                .usuarioId(str(data, "usuarioId"))
                .usuarioNombre(str(data, "usuarioNombre"))
                .motivo(str(data, "motivo"))
                .build();
    }

    public static Map<String, Object> toFirestore(HistorialEstado h) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("solicitudId", h.getSolicitudId());
        m.put("estadoAnterior", h.getEstadoAnterior() != null ? h.getEstadoAnterior().getFirestoreValue() : null);
        m.put("estadoNuevo", h.getEstadoNuevo().getFirestoreValue());
        m.put("fechaCambio", h.getFechaCambio());
        m.put("usuarioId", h.getUsuarioId());
        m.put("usuarioNombre", h.getUsuarioNombre());
        m.put("motivo", h.getMotivo());
        return m;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static Timestamp timestamp(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Timestamp t) return t;
        return null;
    }
}
