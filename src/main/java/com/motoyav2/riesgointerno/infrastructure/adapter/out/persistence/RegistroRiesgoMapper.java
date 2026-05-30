package com.motoyav2.riesgointerno.infrastructure.adapter.out.persistence;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.motoyav2.riesgointerno.domain.enums.EstadoRegistro;
import com.motoyav2.riesgointerno.domain.enums.NivelRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoSujeto;
import com.motoyav2.riesgointerno.domain.model.HistorialCambioRiesgo;
import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class RegistroRiesgoMapper {

    private RegistroRiesgoMapper() {}

    public static RegistroRiesgo toDomain(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Map<String, Object> d = doc.getData();
        if (d == null) return null;

        return RegistroRiesgo.builder()
                .id(doc.getId())
                .dniRegistrado(str(d, "dniRegistrado"))
                .nombreRegistrado(str(d, "nombreRegistrado"))
                .telefonos(listStr(d, "telefonos"))
                .tipoSujeto(TipoSujeto.fromString(str(d, "tipoSujeto")))
                .nivelRiesgo(NivelRiesgo.fromString(str(d, "nivelRiesgo")))
                .estadoRegistro(EstadoRegistro.fromString(str(d, "estadoRegistro")))
                .tipoRiesgo(TipoRiesgo.fromString(str(d, "tipoRiesgo")))
                .contratoIdRelacionado(str(d, "contratoIdRelacionado"))
                .solicitudIdRelacionado(str(d, "solicitudIdRelacionado"))
                .montoDeudaPendiente(dbl(d, "montoDeudaPendiente"))
                .fechaIncidente(ts(d, "fechaIncidente"))
                .descripcion(str(d, "descripcion"))
                .evidencias(listStr(d, "evidencias"))
                .condicionesRehabilitacion(listStr(d, "condicionesRehabilitacion"))
                .registradoPor(str(d, "registradoPor"))
                .historialCambios(mapHistorial(d.get("historialCambios")))
                .fechaRegistro(ts(d, "fechaRegistro"))
                .updatedAt(ts(d, "updatedAt"))
                .build();
    }

    public static Map<String, Object> toFirestore(RegistroRiesgo r) {
        var map = new java.util.HashMap<String, Object>();
        putIfNotNull(map, "dniRegistrado", r.getDniRegistrado());
        map.put("nombreRegistrado", r.getNombreRegistrado());
        map.put("telefonos", r.getTelefonos() != null ? r.getTelefonos() : List.of());
        map.put("tipoSujeto", r.getTipoSujeto() != null ? r.getTipoSujeto().name() : null);
        map.put("nivelRiesgo", r.getNivelRiesgo() != null ? r.getNivelRiesgo().name() : null);
        map.put("estadoRegistro", r.getEstadoRegistro() != null ? r.getEstadoRegistro().name() : null);
        map.put("tipoRiesgo", r.getTipoRiesgo() != null ? r.getTipoRiesgo().name() : null);
        putIfNotNull(map, "contratoIdRelacionado", r.getContratoIdRelacionado());
        putIfNotNull(map, "solicitudIdRelacionado", r.getSolicitudIdRelacionado());
        putIfNotNull(map, "montoDeudaPendiente", r.getMontoDeudaPendiente());
        map.put("fechaIncidente", r.getFechaIncidente());
        map.put("descripcion", r.getDescripcion());
        map.put("evidencias", r.getEvidencias() != null ? r.getEvidencias() : List.of());
        map.put("condicionesRehabilitacion", r.getCondicionesRehabilitacion() != null ? r.getCondicionesRehabilitacion() : List.of());
        map.put("registradoPor", r.getRegistradoPor());
        map.put("historialCambios", List.of());
        map.put("fechaRegistro", r.getFechaRegistro());
        map.put("updatedAt", r.getUpdatedAt());
        return map;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> d, String key) {
        Object v = d.get(key);
        return v instanceof String s ? s : null;
    }

    private static Double dbl(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Timestamp ts(Map<String, Object> d, String key) {
        Object v = d.get(key);
        return v instanceof Timestamp t ? t : null;
    }

    private static List<String> listStr(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (!(v instanceof List<?> list)) return Collections.emptyList();
        return list.stream()
                .filter(e -> e instanceof String)
                .map(e -> (String) e)
                .toList();
    }

    private static List<HistorialCambioRiesgo> mapHistorial(Object raw) {
        if (!(raw instanceof List<?> list)) return Collections.emptyList();
        return list.stream()
                .filter(e -> e instanceof Map)
                .map(e -> {
                    Map<String, Object> m = (Map<String, Object>) e;
                    return HistorialCambioRiesgo.builder()
                            .fecha(m.get("fecha") instanceof Timestamp t ? t : null)
                            .usuario(m.get("usuario") instanceof String s ? s : "")
                            .cambio(m.get("cambio") instanceof String s ? s : "")
                            .motivoCambio(m.get("motivoCambio") instanceof String s ? s : "")
                            .build();
                })
                .toList();
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
