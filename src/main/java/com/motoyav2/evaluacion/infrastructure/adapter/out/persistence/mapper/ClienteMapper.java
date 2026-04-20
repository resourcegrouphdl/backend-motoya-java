package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.motoyav2.evaluacion.domain.model.AlertaEntrevista;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.model.EvaluacionDocumento;
import com.motoyav2.evaluacion.domain.model.EvaluacionEntrevista;
import com.motoyav2.evaluacion.domain.model.ValidacionEmail;
import com.motoyav2.evaluacion.domain.model.VerificacionIdentidadSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public final class ClienteMapper {

    private ClienteMapper() {}

    public static Cliente toDomain(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Map<String, Object> data = doc.getData();
        if (data == null) return null;

        return Cliente.builder()
                .id(doc.getId())
                .tipo(str(data, "tipo"))
                .nombres(str(data, "nombres"))
                .apellidoPaterno(str(data, "apellidoPaterno"))
                .apellidoMaterno(str(data, "apellidoMaterno"))
                .sexo(str(data, "sexo"))
                .fechaNacimiento(str(data, "fechaNacimiento")) // ⚠️ string YYYY-MM-DD
                .estadoCivil(str(data, "estadoCivil"))
                .cargasFamiliares(toInt(data.get("cargasFamiliares")))
                .documentType(str(data, "documentType"))
                .documentNumber(str(data, "documentNumber"))
                .nacionalidad(str(data, "nacionalidad"))
                .email(str(data, "email"))
                .telefono1(str(data, "telefono1"))
                .telefono2(str(data, "telefono2"))
                .departamento(str(data, "departamento"))
                .provincia(str(data, "provincia"))
                .distrito(str(data, "distrito"))
                .direccion(str(data, "direccion"))
                .ubicacionGPSCasa(str(data, "ubicacionGPSCasa"))
                .tipoVivienda(str(data, "tipoVivienda"))
                .detalleVivienda(str(data, "detalleVivienda"))
                .referenciaUbicacion(str(data, "referenciaUbicacion"))
                .licenciaConducir(str(data, "licenciaConducir"))
                .numeroLicencia(str(data, "numeroLicencia"))
                .tienePapeletasPendientes(bool(data, "tienePapeletasPendientes"))
                .totalDeudaPapeletas(dbl(data, "totalDeudaPapeletas"))
                .ocupacion(str(data, "ocupacion"))
                .tipoTrabajo(str(data, "tipoTrabajo"))
                .nombreTrabajoEmpresa(str(data, "nombreTrabajoEmpresa"))
                .ubicacionGPSTrabajo(str(data, "ubicacionGPSTrabajo"))
                .comoSustentaIngresos(str(data, "comoSustentaIngresos"))
                .ingresoMensual(dbl(data, "ingresoMensual"))
                .rangoIngresos(str(data, "rangoIngresos"))
                .perfilSentinel(str(data, "perfilSentinel"))
                .totalDeudaBancos(dbl(data, "totalDeudaBancos"))
                .totalOtrasDeudas(dbl(data, "totalOtrasDeudas"))
                .tipoCliente(str(data, "tipoCliente"))
                .archivos(mapArchivos(data.get("archivos")))
                .evaluacionDocumentos(mapEvaluacionDocumentos(data.get("evaluacionDocumentos")))
                .estadoValidacionDocumentos(str(data, "estadoValidacionDocumentos"))
                .documentosObservados(listStr(data, "documentosObservados"))
                .datosVerificados(bool(data, "datosVerificados"))
                .observacionesEvaluador(str(data, "observacionesEvaluador"))
                .evaluacionEntrevista(mapEntrevista(data.get("evaluacionEntrevista")))
                .verificacionIdentidad(mapVerificacionSnapshot(data.get("verificacionIdentidad")))
                .validacionEmail(mapValidacionEmail(data.get("validacionEmail")))
                .createdAt(timestamp(data, "createdAt"))
                .updatedAt(timestamp(data, "updatedAt"))
                .fechaValidacionDocumentos(timestamp(data, "fechaValidacionDocumentos"))
                .build();
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClienteMapper.class);

    private static Map<String, String> mapArchivos(Object raw) {
        // --- DEBUG FIADOR ---
        if (raw == null) {
            log.info("[DEBUG ClienteMapper] archivos raw es NULL");
            return null;
        }
        if (!(raw instanceof Map)) {
            log.info("[DEBUG ClienteMapper] archivos raw NO es Map, es: {}", raw.getClass().getName());
            return null;
        }
        // --- END DEBUG ---
        Map<String, String> result = new HashMap<>();
        ((Map<String, Object>) raw).forEach((k, v) -> {
            if (v != null) result.put(k, v.toString());
        });
        log.info("[DEBUG ClienteMapper] archivos mapeados: keys={}", result.keySet());
        return result;
    }

    private static Map<String, EvaluacionDocumento> mapEvaluacionDocumentos(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<String, EvaluacionDocumento> result = new HashMap<>();
        ((Map<String, Object>) raw).forEach((k, v) -> {
            if (v instanceof Map<?, ?> m) {
                Map<String, Object> em = (Map<String, Object>) m;
                result.put(k, EvaluacionDocumento.builder()
                        .estado(str(em, "estado"))
                        .observaciones(str(em, "observaciones"))
                        .fechaEvaluacion(timestamp(em, "fechaEvaluacion"))
                        .evaluador(str(em, "evaluador"))
                        .build());
            }
        });
        return result;
    }

    private static EvaluacionEntrevista mapEntrevista(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        return EvaluacionEntrevista.builder()
                .solicitudId(str(m, "solicitudId"))
                .fechaInicio(timestamp(m, "fechaInicio"))
                .fechaFin(timestamp(m, "fechaFin"))
                .duracionMinutos(toInt(m.get("duracionMinutos")))
                .entrevistadorId(str(m, "entrevistadorId"))
                .entrevistadorNombre(str(m, "entrevistadorNombre"))
                .modalidad(str(m, "modalidad"))
                .plataforma(str(m, "plataforma"))
                .puntualidad(str(m, "puntualidad"))
                .coordenadasCliente(str(m, "coordenadasCliente"))
                .coordenadasFiador(str(m, "coordenadasFiador"))
                .presentacionPersonal(toInt(m.get("presentacionPersonal")))
                .actitudColaboracion(toInt(m.get("actitudColaboracion")))
                .coherenciaRespuestas(toInt(m.get("coherenciaRespuestas")))
                .nivelConfianza(toInt(m.get("nivelConfianza")))
                .observacionesCliente(str(m, "observacionesCliente"))
                .observacionesFiador(str(m, "observacionesFiador"))
                .observacionesDomicilio(str(m, "observacionesDomicilio"))
                .observacionesCapacidadPago(str(m, "observacionesCapacidadPago"))
                .hallazgosPositivos(listStr(m, "hallazgosPositivos"))
                .hallazgosNegativos(listStr(m, "hallazgosNegativos"))
                .alertas(mapAlertas(m.get("alertas")))
                .scoreEntrevista(toInt(m.get("scoreEntrevista")))
                .recomendacion(str(m, "recomendacion"))
                .motivoRecomendacion(str(m, "motivoRecomendacion"))
                .condiciones(listStr(m, "condiciones"))
                .meetingUrl(str(m, "meetingUrl"))
                .meetingId(str(m, "meetingId"))
                .meetingScheduled(bool(m, "meetingScheduled"))
                .meetingScheduledDate(timestamp(m, "meetingScheduledDate"))
                .whatsappMessageSent(bool(m, "whatsappMessageSent"))
                .whatsappMessageDate(timestamp(m, "whatsappMessageDate"))
                .esBorrador(bool(m, "esBorrador"))
                .createdAt(timestamp(m, "createdAt"))
                .updatedAt(timestamp(m, "updatedAt"))
                .build();
    }

    private static List<AlertaEntrevista> mapAlertas(Object raw) {
        if (!(raw instanceof List)) return List.of();
        return ((List<Object>) raw).stream()
                .filter(a -> a instanceof Map)
                .map(a -> {
                    Map<String, Object> m = (Map<String, Object>) a;
                    return AlertaEntrevista.builder()
                            .tipo(str(m, "tipo"))
                            .descripcion(str(m, "descripcion"))
                            .severidad(str(m, "severidad"))
                            .timestamp(timestamp(m, "timestamp"))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static VerificacionIdentidadSnapshot mapVerificacionSnapshot(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        Object exitosoRaw = m.get("exitoso");
        boolean exitoso = exitosoRaw instanceof Boolean b ? b
                : exitosoRaw != null && Boolean.parseBoolean(exitosoRaw.toString());
        return new VerificacionIdentidadSnapshot(
                exitoso,
                str(m, "apiNombres"),
                str(m, "apiApellidoPaterno"),
                str(m, "apiApellidoMaterno")
        );
    }

    private static ValidacionEmail mapValidacionEmail(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        Object validoRaw = m.get("valido");
        boolean valido = validoRaw instanceof Boolean b ? b
                : validoRaw != null && Boolean.parseBoolean(validoRaw.toString());
        return new ValidacionEmail(
                valido,
                str(m, "nivel"),
                str(m, "detalle"),
                timestamp(m, "verificadoEn")
        );
    }

    // ── helpers ────────────────────────────────────────────────────────────

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

    private static List<String> listStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }
}
