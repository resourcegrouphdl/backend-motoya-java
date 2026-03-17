package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.model.AlertaEntrevista;
import com.motoyav2.evaluacion.domain.model.EntrevistaCompleta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsea el Map<String, Object> de evaluacionEntrevista (embebido en clientes_v1)
 * hacia el domain model EntrevistaCompleta.
 */
public final class EntrevistaMapper {

    private EntrevistaMapper() {}

    @SuppressWarnings("unchecked")
    public static EntrevistaCompleta fromMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return null;

        return EntrevistaCompleta.builder()
                .solicitudId(str(raw, "solicitudId"))
                .entrevistadorId(str(raw, "entrevistadorId"))
                .entrevistadorNombre(str(raw, "entrevistadorNombre"))
                .modalidad(str(raw, "modalidad"))
                .puntualidad(str(raw, "puntualidad"))
                .presentacionPersonal(intVal(raw, "presentacionPersonal"))
                .actitudColaboracion(intVal(raw, "actitudColaboracion"))
                .coherenciaRespuestas(intVal(raw, "coherenciaRespuestas"))
                .nivelConfianza(intVal(raw, "nivelConfianza"))
                .observacionesCliente(str(raw, "observacionesCliente"))
                .observacionesFiador(str(raw, "observacionesFiador"))
                .observacionesDomicilio(str(raw, "observacionesDomicilio"))
                .observacionesCapacidadPago(str(raw, "observacionesCapacidadPago"))
                .hallazgosPositivos(strList(raw, "hallazgosPositivos"))
                .hallazgosNegativos(strList(raw, "hallazgosNegativos"))
                .alertas(parsearAlertas(raw))
                .scoreEntrevista(intVal(raw, "scoreEntrevista"))
                .recomendacion(str(raw, "recomendacion"))
                .motivoRecomendacion(str(raw, "motivoRecomendacion"))
                .condiciones(strList(raw, "condiciones"))
                .esBorrador(boolVal(raw, "esBorrador"))
                .fechaInicio(timestampStr(raw, "fechaInicio"))
                .fechaFin(timestampStr(raw, "fechaFin"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<AlertaEntrevista> parsearAlertas(Map<String, Object> raw) {
        Object alertasRaw = raw.get("alertas");
        if (!(alertasRaw instanceof List<?> lista)) return List.of();
        List<AlertaEntrevista> alertas = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> a = (Map<String, Object>) m;
                alertas.add(AlertaEntrevista.builder()
                        .tipo(str(a, "tipo"))
                        .descripcion(str(a, "descripcion"))
                        .severidad(str(a, "severidad"))
                        .timestamp(timestampStr(a, "timestamp"))
                        .build());
            }
        }
        return alertas;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static Integer intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private static Boolean boolVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof List<?> l)) return List.of();
        return l.stream().map(Object::toString).toList();
    }

    private static String timestampStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
