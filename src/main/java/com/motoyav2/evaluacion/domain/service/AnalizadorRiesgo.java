package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.AlertaEntrevista;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.riesgo.FlagRiesgo;
import com.motoyav2.evaluacion.domain.model.riesgo.NivelRiesgo;
import com.motoyav2.evaluacion.domain.model.riesgo.PerfilRiesgo;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service puro — sin I/O.
 * Analiza el expediente completo y genera flags de riesgo.
 * El nivel general de riesgo se determina por la combinación de flags.
 */
@Component
public class AnalizadorRiesgo {

    public PerfilRiesgo analizar(Persona titular, Persona fiador,
                                  List<ReferenciasDelTitular> referencias,
                                  ScoreResult scoreResult) {
        List<FlagRiesgo> flags = new ArrayList<>();

        analizarSentinel(titular, flags, "titular");
        if (fiador != null) analizarSentinel(fiador, flags, "fiador");

        analizarDocumentacion(titular, flags, "titular");
        if (fiador != null) analizarDocumentacion(fiador, flags, "fiador");

        analizarPapeletas(titular, flags);
        analizarEntrevista(titular, flags);
        analizarReferencias(referencias, flags);
        analizarCapacidadPago(scoreResult, flags);
        analizarScoreGeneral(scoreResult, flags);
        analizarCasoSinFiador(titular, fiador, flags);

        return buildPerfil(flags);
    }

    private void analizarSentinel(Persona persona, List<FlagRiesgo> flags, String origen) {
        String sentinel = persona.getPerfilSentinel();
        if (sentinel == null) return;
        if (sentinel.startsWith("rojo")) {
            flags.add(flag(FlagRiesgo.TipoFlag.SENTINEL_NEGATIVO, NivelRiesgo.CRITICO,
                    "Perfil Sentinel negativo — historial de no pago", origen));
        } else if (sentinel.startsWith("amarillo")) {
            flags.add(flag(FlagRiesgo.TipoFlag.SENTINEL_MODERADO, NivelRiesgo.MEDIO,
                    "Perfil Sentinel moderado — atrasos en pagos", origen));
        }
    }

    private void analizarDocumentacion(Persona persona, List<FlagRiesgo> flags, String origen) {
        String lic = persona.getLicenciaDeConducir();
        if (lic != null && lic.toLowerCase().contains("venc")) {
            flags.add(flag(FlagRiesgo.TipoFlag.LICENCIA_VENCIDA, NivelRiesgo.ALTO,
                    "Licencia de conducir vencida", origen));
        }
        String estadoCE = persona.getEstadoResidenciaCE();
        String tipDoc   = persona.getTipoDeDocumento();
        if (("CE".equalsIgnoreCase(tipDoc) || "Pasaporte".equalsIgnoreCase(tipDoc))
                && "vencido".equalsIgnoreCase(estadoCE)) {
            flags.add(flag(FlagRiesgo.TipoFlag.DOCUMENTO_IDENTIDAD_VENCIDO, NivelRiesgo.ALTO,
                    "Documento de identidad (" + tipDoc + ") vencido", origen));
        }
    }

    private void analizarPapeletas(Persona titular, List<FlagRiesgo> flags) {
        if (Boolean.TRUE.equals(titular.getTienePapeletasPendientes())) {
            double monto = titular.getTotalDeudaPapeletas() != null ? titular.getTotalDeudaPapeletas() : 0;
            NivelRiesgo nivel = monto > 1000 ? NivelRiesgo.ALTO : NivelRiesgo.MEDIO;
            flags.add(flag(FlagRiesgo.TipoFlag.PAPELETAS_PENDIENTES, nivel,
                    "Titular con papeletas pendientes" + (monto > 0 ? " — S/ " + monto : ""), "titular"));
        }
    }

    private void analizarEntrevista(Persona titular, List<FlagRiesgo> flags) {
        var entrevista = titular.getEvaluacionEntrevista();
        if (entrevista == null) {
            flags.add(flag(FlagRiesgo.TipoFlag.SIN_ENTREVISTA, NivelRiesgo.MEDIO,
                    "No se ha realizado entrevista de evaluación", "entrevista"));
            return;
        }
        List<AlertaEntrevista> alertas = entrevista.getAlertas() != null ? entrevista.getAlertas() : List.of();
        for (AlertaEntrevista alerta : alertas) {
            if (alerta.esCritica()) {
                flags.add(flag(FlagRiesgo.TipoFlag.ALERTA_ENTREVISTA_CRITICA, NivelRiesgo.CRITICO,
                        "Alerta crítica en entrevista: " + alerta.getDescripcion(), "entrevista"));
            } else if (alerta.esAlta()) {
                flags.add(flag(FlagRiesgo.TipoFlag.ALERTA_ENTREVISTA_ALTA, NivelRiesgo.ALTO,
                        "Alerta alta en entrevista: " + alerta.getDescripcion(), "entrevista"));
            }
        }
    }

    private void analizarReferencias(List<ReferenciasDelTitular> referencias, List<FlagRiesgo> flags) {
        if (referencias == null || referencias.isEmpty()) {
            flags.add(flag(FlagRiesgo.TipoFlag.REFERENCIAS_INSUFICIENTES, NivelRiesgo.MEDIO,
                    "Sin referencias personales registradas", "referencias"));
            return;
        }
        long rechazadas = referencias.stream()
                .filter(r -> Boolean.TRUE.equals(r.getRechazada())
                        || "rechazado".equalsIgnoreCase(r.getEstadoVerificacion()))
                .count();
        if (rechazadas >= 2) {
            flags.add(flag(FlagRiesgo.TipoFlag.REFERENCIAS_NEGATIVAS, NivelRiesgo.CRITICO,
                    rechazadas + " referencias rechazadas", "referencias"));
        } else if (rechazadas == 1) {
            flags.add(flag(FlagRiesgo.TipoFlag.REFERENCIAS_NEGATIVAS, NivelRiesgo.ALTO,
                    "1 referencia rechazada", "referencias"));
        }
        long verificadas = referencias.stream()
                .filter(r -> "verificado".equalsIgnoreCase(r.getEstadoVerificacion()))
                .count();
        if (verificadas == 0 && referencias.size() > 0) {
            flags.add(flag(FlagRiesgo.TipoFlag.REFERENCIAS_INSUFICIENTES, NivelRiesgo.MEDIO,
                    "Ninguna referencia verificada aún", "referencias"));
        }
    }

    private void analizarCapacidadPago(ScoreResult scoreResult, List<FlagRiesgo> flags) {
        var cap = scoreResult.getCapacidadDePago();
        if (cap == null) return;
        double ratio = cap.getRatioCuotaIngreso();
        if (ratio > 0.50) {
            flags.add(flag(FlagRiesgo.TipoFlag.RATIO_CUOTA_MUY_ALTO, NivelRiesgo.CRITICO,
                    String.format("Cuota representa el %.0f%% del ingreso (máx. 35%%)", ratio * 100), "financiamiento"));
        } else if (ratio > 0.35) {
            flags.add(flag(FlagRiesgo.TipoFlag.RATIO_CUOTA_ALTO, NivelRiesgo.ALTO,
                    String.format("Cuota representa el %.0f%% del ingreso (máx. 35%%)", ratio * 100), "financiamiento"));
        }
        if (cap.getIngresoMensualEstimado() == 0) {
            flags.add(flag(FlagRiesgo.TipoFlag.INGRESOS_NO_COMPROBABLES, NivelRiesgo.ALTO,
                    "No se registró ingreso mensual ni rango de ingresos", "titular"));
        }
        if ("INSUFICIENTE".equals(cap.getNivelCapacidad())) {
            flags.add(flag(FlagRiesgo.TipoFlag.CAPACIDAD_PAGO_INSUFICIENTE, NivelRiesgo.CRITICO,
                    "Capacidad de pago insuficiente para asumir la cuota", "financiamiento"));
        }
    }

    private void analizarScoreGeneral(ScoreResult scoreResult, List<FlagRiesgo> flags) {
        if (scoreResult.getScoreFinal() < 40) {
            flags.add(flag(FlagRiesgo.TipoFlag.SCORE_FINAL_BAJO, NivelRiesgo.CRITICO,
                    String.format("Score final %.1f — por debajo del mínimo (40)", scoreResult.getScoreFinal()), "evaluacion"));
        }
        if (scoreResult.getScoreDocumental() != null && scoreResult.getScoreDocumental().getValor() < 30) {
            flags.add(flag(FlagRiesgo.TipoFlag.SCORE_DOCUMENTAL_BAJO, NivelRiesgo.ALTO,
                    "Score documental muy bajo — documentación incompleta", "titular"));
        }
    }

    private void analizarCasoSinFiador(Persona titular, Persona fiador, List<FlagRiesgo> flags) {
        if (fiador != null) return;
        String ec = titular.getEstadoCivil();
        if (ec != null && ec.toLowerCase().contains("casad")) {
            flags.add(flag(FlagRiesgo.TipoFlag.SIN_FIADOR_CASADO, NivelRiesgo.BAJO,
                    "Titular casado sin conyugue como fiador", "titular"));
        }
    }

    private PerfilRiesgo buildPerfil(List<FlagRiesgo> flags) {
        int criticos = (int) flags.stream().filter(f -> f.getSeveridad() == NivelRiesgo.CRITICO).count();
        int altos    = (int) flags.stream().filter(f -> f.getSeveridad() == NivelRiesgo.ALTO).count();
        int medios   = (int) flags.stream().filter(f -> f.getSeveridad() == NivelRiesgo.MEDIO).count();

        NivelRiesgo nivel;
        if      (criticos > 0)     nivel = NivelRiesgo.CRITICO;
        else if (altos >= 2)       nivel = NivelRiesgo.ALTO;
        else if (altos == 1 || medios >= 2) nivel = NivelRiesgo.MEDIO;
        else                       nivel = NivelRiesgo.BAJO;

        return PerfilRiesgo.builder()
                .nivelGeneral(nivel)
                .flags(List.copyOf(flags))
                .totalFlags(flags.size())
                .flagsCriticos(criticos)
                .flagsAltos(altos)
                .flagsMedios(medios)
                .build();
    }

    private FlagRiesgo flag(FlagRiesgo.TipoFlag tipo, NivelRiesgo sev, String desc, String origen) {
        return FlagRiesgo.builder().tipo(tipo).severidad(sev).descripcion(desc).origen(origen).build();
    }
}
