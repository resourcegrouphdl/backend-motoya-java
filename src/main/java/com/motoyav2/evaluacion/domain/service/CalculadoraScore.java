package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.AlertaEntrevista;
import com.motoyav2.evaluacion.domain.model.EntrevistaCompleta;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.scoring.CapacidadDePagoCalculo;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreDocumental;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreEntrevista;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreReferencias;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain Service puro — sin I/O.
 * Centraliza todos los cálculos de scoring del expediente.
 *
 * Pesos del score final:
 *   Con fiador:    documental 30% | garantes 20% | entrevista 30% | referencias 20%
 *   Sin fiador:    documental 40% | entrevista 35% | referencias 25%
 */
@Component
@RequiredArgsConstructor
public class CalculadoraScore {

    private final CalculadoraCapacidadDePago calculadoraCapacidadDePago;

    // ── Documentos requeridos por tipo de persona ───────────────────────────
    private static final Set<String> DOCS_REQUERIDOS_TITULAR = Set.of(
            "dniFrente", "dniReverso", "selfie",
            "fotoLicenciaFrente", "fotoLicenciaReverso",
            "certificadoLaboral", "reciboServicio", "fachada"
    );
    private static final Set<String> DOCS_REQUERIDOS_FIADOR = Set.of(
            "fiadorDniFrente", "fiadorDniReverso",
            "fiadorLicenciaFrente", "fiadorLicenciaReverso",
            "fiadorCertificadoLaboral", "fiadorReciboServicio", "fiadorFachada"
    );

    // ── Pesos documentales ──────────────────────────────────────────────────
    /** Pesos por tipo de documento del titular — accesible para servicios externos. */
    public static final Map<String, Double> PESOS_DOC_TITULAR = Map.of(
            "dniFrente",             10.0,
            "dniReverso",            10.0,
            "selfie",                 5.0,
            "fotoLicenciaFrente",     7.5,
            "fotoLicenciaReverso",    7.5,
            "certificadoLaboral",    20.0,
            "reciboServicio",        15.0,
            "fachada",               10.0
    );

    // Alias interno para usar en calcularTodo
    private static final Map<String, Double> PESOS_DOC = PESOS_DOC_TITULAR;
    private static final double PESO_DOC_OTROS = 2.5; // para docs extra

    public ScoreResult calcularTodo(Persona titular, Persona fiador,
                                    List<ReferenciasDelTitular> referencias,
                                    String montoCuotaStr) {
        ScoreDocumental docTitular  = calcularScoreDocumental(titular, DOCS_REQUERIDOS_TITULAR, PESOS_DOC);
        ScoreDocumental docFiador   = fiador != null
                ? calcularScoreDocumental(fiador, DOCS_REQUERIDOS_FIADOR, buildPesosFiador())
                : null;

        ScoreEntrevista entrevista  = calcularScoreEntrevista(titular.getEvaluacionEntrevista());
        ScoreReferencias refs       = calcularScoreReferencias(referencias);
        CapacidadDePagoCalculo cap  = calculadoraCapacidadDePago.calcular(titular, montoCuotaStr);

        double scoreFinal = calcularScoreFinal(docTitular, docFiador, entrevista, refs, fiador != null);
        String descripcion = fiador != null
                ? "documental(30%) + garantes(20%) + entrevista(30%) + referencias(20%)"
                : "documental(40%) + entrevista(35%) + referencias(25%)";

        return ScoreResult.builder()
                .scoreDocumental(docTitular)
                .scoreGarantes(docFiador)
                .scoreEntrevista(entrevista)
                .scoreReferencias(refs)
                .capacidadDePago(cap)
                .scoreFinal(round(scoreFinal))
                .tieneFiador(fiador != null)
                .descripcionPonderacion(descripcion)
                .build();
    }

    // ── Score Documental ────────────────────────────────────────────────────

    public ScoreDocumental calcularScoreDocumental(Persona persona,
                                                    Set<String> docsRequeridos,
                                                    Map<String, Double> pesos) {
        if (persona == null) return ScoreDocumental.cero();

        Map<String, String> archivos = persona.getDocumentos() != null
                ? buildArchivosTipoMap(persona)
                : Map.of();

        Map<String, Map<String, Object>> evalDocs = persona.getEvaluacionDocumentos();

        int subidos = 0, aprobados = 0, observados = 0;
        double puntosObtenidos = 0.0;
        double puntosMaximos = pesos.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, String> estadoPorDoc = new HashMap<>();

        for (String tipoDoc : docsRequeridos) {
            String normalizedKey = normalizarKeyFiador(tipoDoc);
            boolean presente = archivos.containsKey(normalizedKey) || archivos.containsKey(tipoDoc);

            String estadoDoc = "pendiente";
            if (evalDocs != null) {
                Map<String, Object> eval = evalDocs.getOrDefault(normalizedKey, evalDocs.get(tipoDoc));
                if (eval != null) {
                    estadoDoc = String.valueOf(eval.getOrDefault("estado", "pendiente"));
                }
            }

            estadoPorDoc.put(tipoDoc, presente ? estadoDoc : "ausente");

            if (presente) {
                subidos++;
                double peso = pesos.getOrDefault(normalizedKey, pesos.getOrDefault(tipoDoc, PESO_DOC_OTROS));
                switch (estadoDoc) {
                    case "aprobado"  -> { aprobados++; puntosObtenidos += peso; }
                    case "pendiente" -> puntosObtenidos += peso * 0.5;  // presente pero no evaluado
                    case "observado" -> { observados++; puntosObtenidos += peso * 0.2; }
                    case "rechazado" -> observados++;
                }
            }
        }

        double completitud = docsRequeridos.isEmpty() ? 0 : (double) subidos / docsRequeridos.size() * 100;
        double aprobacion  = subidos == 0 ? 0 : (double) aprobados / subidos * 100;
        double valorBase   = puntosMaximos > 0 ? puntosObtenidos / puntosMaximos * 100 : 0;

        // penalización por licencia vencida
        boolean licVigente = esLicenciaVigente(persona);
        boolean docIdVigente = esDocumentoPrincipalVigente(persona);
        if (!licVigente) valorBase = Math.max(0, valorBase - 10);
        if (!docIdVigente) valorBase = Math.max(0, valorBase - 15);

        // bonus por todos aprobados
        if (aprobados == docsRequeridos.size() && aprobados > 0) valorBase = Math.min(100, valorBase + 5);

        return ScoreDocumental.builder()
                .valor(round(valorBase))
                .completitud(round(completitud))
                .aprobacion(round(aprobacion))
                .docsSubidos(subidos)
                .docsAprobados(aprobados)
                .docsObservados(observados)
                .docsRequeridos(docsRequeridos.size())
                .estadoPorDocumento(estadoPorDoc)
                .licenciaVigente(licVigente)
                .documentoIdentidadVigente(docIdVigente)
                .build();
    }

    // ── Score Entrevista ────────────────────────────────────────────────────

    public ScoreEntrevista calcularScoreEntrevista(EntrevistaCompleta entrevista) {
        if (entrevista == null) return ScoreEntrevista.sinEntrevista();
        if (Boolean.TRUE.equals(entrevista.getEsBorrador())) return ScoreEntrevista.sinEntrevista();

        int p1 = safeInt(entrevista.getPresentacionPersonal(), 0);
        int p2 = safeInt(entrevista.getActitudColaboracion(), 0);
        int p3 = safeInt(entrevista.getCoherenciaRespuestas(), 0);
        int p4 = safeInt(entrevista.getNivelConfianza(), 0);

        double factorP = p1 * 0.15 * 20;
        double factorA = p2 * 0.25 * 20;
        double factorC = p3 * 0.40 * 20;
        double factorN = p4 * 0.20 * 20;
        double base    = factorP + factorA + factorC + factorN;

        // penalización por puntualidad
        double penPuntualidad = switch (safeStr(entrevista.getPuntualidad())) {
            case "retraso_leve"          -> -5;
            case "retraso_significativo" -> -15;
            case "no_asistio"            -> -60;
            default                      -> 0;
        };

        // penalización por alertas
        List<AlertaEntrevista> alertas = entrevista.getAlertas() != null ? entrevista.getAlertas() : List.of();
        long criticas = alertas.stream().filter(AlertaEntrevista::esCritica).count();
        long altas    = alertas.stream().filter(AlertaEntrevista::esAlta).count();
        double penAlertas = Math.max(-40, criticas * -20.0 + altas * -10.0);

        double valor = Math.max(0, Math.min(100, base + penPuntualidad + penAlertas));

        return ScoreEntrevista.builder()
                .valor(round(valor))
                .factorPresentacion(round(factorP))
                .factorActitud(round(factorA))
                .factorCoherencia(round(factorC))
                .factorConfianza(round(factorN))
                .penalizacionPuntualidad(penPuntualidad)
                .penalizacionAlertas(penAlertas)
                .alertasCriticas((int) criticas)
                .alertasAltas((int) altas)
                .entrevistaRealizada(true)
                .recomendacion(entrevista.getRecomendacion())
                .build();
    }

    // ── Score Referencias ───────────────────────────────────────────────────

    public ScoreReferencias calcularScoreReferencias(List<ReferenciasDelTitular> referencias) {
        if (referencias == null || referencias.isEmpty()) return ScoreReferencias.sinReferencias();

        int total = referencias.size();
        int verificadas = 0, noContactadas = 0, rechazadas = 0;
        double sumaScores = 0;

        for (ReferenciasDelTitular ref : referencias) {
            String estado = safeStr(ref.getEstadoVerificacion());
            if ("verificado".equals(estado)) {
                verificadas++;
                double score = ref.getScoreDeVerificacionNum() != null ? ref.getScoreDeVerificacionNum() : 50.0;
                sumaScores += score;
            } else if ("rechazado".equals(estado) || Boolean.TRUE.equals(ref.getRechazada())) {
                rechazadas++;
            } else if ("no_contactado".equals(estado)) {
                noContactadas++;
            }
        }

        double promedio = verificadas > 0 ? sumaScores / verificadas : 0;
        double penRechazadas = Math.max(-40, rechazadas * -20.0); // max -40
        double valor = Math.max(0, Math.min(100, promedio + penRechazadas));

        // bonus si todas verificadas
        if (verificadas == total && total > 0) valor = Math.min(100, valor + 5);

        return ScoreReferencias.builder()
                .valor(round(valor))
                .totalReferencias(total)
                .verificadas(verificadas)
                .noContactadas(noContactadas)
                .rechazadas(rechazadas)
                .promedioVerificadas(round(promedio))
                .penalizacionRechazadas(penRechazadas)
                .build();
    }

    // ── Score Final ─────────────────────────────────────────────────────────

    private double calcularScoreFinal(ScoreDocumental doc, ScoreDocumental garantes,
                                      ScoreEntrevista entrevista, ScoreReferencias refs,
                                      boolean tieneFiador) {
        if (tieneFiador) {
            double garantesValor = garantes != null ? garantes.getValor() : 0;
            return doc.getValor()          * 0.30
                 + garantesValor           * 0.20
                 + entrevista.getValor()   * 0.30
                 + refs.getValor()         * 0.20;
        } else {
            return doc.getValor()          * 0.40
                 + entrevista.getValor()   * 0.35
                 + refs.getValor()         * 0.25;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, Double> buildPesosFiador() {
        Map<String, Double> pesos = new HashMap<>();
        pesos.put("fiadorDniFrente",           10.0);
        pesos.put("fiadorDniReverso",           10.0);
        pesos.put("fiadorLicenciaFrente",        7.5);
        pesos.put("fiadorLicenciaReverso",       7.5);
        pesos.put("fiadorCertificadoLaboral",   20.0);
        pesos.put("fiadorReciboServicio",       15.0);
        pesos.put("fiadorFachada",              10.0);
        return pesos;
    }

    private Map<String, String> buildArchivosTipoMap(Persona persona) {
        Map<String, String> map = new HashMap<>();
        if (persona.getDocumentos() != null) {
            persona.getDocumentos().forEach(d -> {
                if (d.getTipoDocumento() != null) map.put(d.getTipoDocumento(), d.getUrl());
            });
        }
        return map;
    }

    /** Normaliza keys fiador: "fiadorDniFrente" → "dniFrente" para lookup en pesos */
    private String normalizarKeyFiador(String tipo) {
        if (tipo.startsWith("fiador")) {
            String sin = tipo.substring("fiador".length());
            return Character.toLowerCase(sin.charAt(0)) + sin.substring(1);
        }
        return tipo;
    }

    private boolean esLicenciaVigente(Persona persona) {
        String lic = persona.getLicenciaDeConducir();
        return lic == null || !lic.toLowerCase().contains("venc");
    }

    private boolean esDocumentoPrincipalVigente(Persona persona) {
        String est = persona.getEstadoResidenciaCE();
        String doc = persona.getTipoDeDocumento();
        if ("CE".equalsIgnoreCase(doc) || "Pasaporte".equalsIgnoreCase(doc)) {
            return !"vencido".equalsIgnoreCase(est);
        }
        return true; // DNI peruano no vence para este efecto
    }

    private int safeInt(Integer v, int def) { return v != null ? v : def; }
    private String safeStr(String v) { return v != null ? v : ""; }
    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
