package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.motoyav2.evaluacion.domain.enums.Decision;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.model.DatosFinancieros;
import com.motoyav2.evaluacion.domain.model.DatosVendedor;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class SolicitudMapper {

    private SolicitudMapper() {}

    public static Solicitud toDomain(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Map<String, Object> data = doc.getData();
        if (data == null) return null;

        return Solicitud.builder()
                .id(doc.getId())
                .numeroSolicitud(str(data, "numeroSolicitud"))
                .codigoDeSolicitud(str(data, "codigoDeSolicitud"))
                .estado(EstadoSolicitud.fromFirestoreValue(str(data, "estado")))
                .prioridad(str(data, "prioridad"))
                .titularId(str(data, "titularId"))
                .fiadorId(str(data, "fiadorId"))
                .vehiculoId(str(data, "vehiculoId"))
                .referenciasIds(listStr(data, "referenciasIds"))
                // Financiero legacy — ⚠️ plazoQuincenas puede ser String o Number
                .precioCompraMoto(toBD(data.get("precioCompraMoto")))
                .inicial(toBD(data.get("inicial")))
                .montoCuota(toBD(data.get("montoCuota")))
                .plazoQuincenas(FirestoreUtils.toInt(data.get("plazoQuincenas"), 0))
                // Financiero nuevo
                .datosFinancieros(mapDatosFinancieros(data.get("datosFinancieros")))
                // Vendedor
                .vendedor(mapVendedor(data.get("vendedor")))
                .vendedorId(str(data, "vendedorId"))
                .vendedorNombre(str(data, "vendedorNombre"))
                .mensajeOpcional(str(data, "mensajeOpcional"))
                // Asesor
                .asesorAsignadoId(str(data, "asesorAsignadoId"))
                .fechaAsignacion(timestamp(data, "fechaAsignacion"))
                // Scores
                .scoreDocumental(dbl(data, "scoreDocumental"))
                .scoreGarantes(dbl(data, "scoreGarantes"))
                .scoreEntrevista(dbl(data, "scoreEntrevista"))
                .scoreFinal(dbl(data, "scoreFinal"))
                // Decisión
                .decisionFinal(Decision.fromValue(str(data, "decisionFinal")))
                .montoAprobado(toBD(data.get("montoAprobado")))
                .motivoRechazo(str(data, "motivoRechazo"))
                .motivoDecision(str(data, "motivoDecision"))
                .fechaDecisionFinal(timestamp(data, "fechaDecisionFinal"))
                .usuarioDecision(str(data, "usuarioDecision"))
                .condicionesAprobacion(listStr(data, "condicionesAprobacion"))
                .fortalezasCaso(str(data, "fortalezasCaso"))
                .debilidadesCaso(str(data, "debilidadesCaso"))
                .resultadoFinal(str(data, "resultadoFinal"))
                .evaluador(str(data, "evaluador"))
                // Documentos generados
                .certificadoGenerado(bool(data, "certificadoGenerado"))
                .urlCertificado(str(data, "urlCertificado"))
                .fechaGeneracionCertificado(timestamp(data, "fechaGeneracionCertificado"))
                .contratoGenerado(bool(data, "contratoGenerado"))
                .urlContrato(str(data, "urlContrato"))
                .fechaGeneracionContrato(timestamp(data, "fechaGeneracionContrato"))
                .observacionesGenerales(str(data, "observacionesGenerales"))
                .createdAt(timestamp(data, "createdAt"))
                .updatedAt(timestamp(data, "updatedAt"))
                .build();
    }

    private static DatosFinancieros mapDatosFinancieros(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        return DatosFinancieros.builder()
                .montoVehiculo(toBD(m.get("montoVehiculo")))
                .soatCostosNotariales(toBD(m.get("soatCostosNotariales")))
                .costoTotal(toBD(m.get("costoTotal")))
                .inicial(toBD(m.get("inicial")))
                .montoFinanciar(toBD(m.get("montoFinanciar")))
                .numeroCuotasQuincenales(FirestoreUtils.toInt(m.get("numeroCuotasQuincenales"), 0))
                .montoCuotaQuincenal(toBD(m.get("montoCuotaQuincenal")))
                .montoAbonarDealer(toBD(m.get("montoAbonarDealer")))
                .totalAPagar(toBD(m.get("totalAPagar")))
                .porcentajeInicial(toBD(m.get("porcentajeInicial")))
                .build();
    }

    private static DatosVendedor mapVendedor(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        return DatosVendedor.builder()
                .id(str(m, "id"))
                .nombre(str(m, "nombre"))
                .tienda(str(m, "tienda"))
                .email(str(m, "email"))
                .telefono(str(m, "telefono"))
                .build();
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

    private static BigDecimal toBD(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> listStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
