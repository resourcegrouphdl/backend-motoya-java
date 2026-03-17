package com.motoyav2.evaluacion.application.service;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.port.in.EvaluarDocumentosUseCase;
import com.motoyav2.evaluacion.application.port.out.ClienteActualizacionPort;
import com.motoyav2.evaluacion.application.port.out.ClientePort;
import com.motoyav2.evaluacion.application.port.out.SolicitudActualizacionPort;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreDocumental;
import com.motoyav2.evaluacion.domain.service.CalculadoraScore;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos.EvaluarDocumentosRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos.EvaluarDocumentosResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluarDocumentosService implements EvaluarDocumentosUseCase {

    private final ClientePort clientePort;
    private final ClienteActualizacionPort clienteActualizacion;
    private final SolicitudActualizacionPort solicitudActualizacion;
    private final CalculadoraScore calculadoraScore;

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

    @Override
    public Mono<EvaluarDocumentosResponse> ejecutar(String solicitudId, EvaluarDocumentosRequest request) {
        String clienteId = request.getClienteId();

        return clientePort.buscarPorId(clienteId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Cliente no encontrado: " + clienteId)))
                .flatMap(persona -> {
                    // Merge: evaluaciones previas + nuevas evaluaciones del request
                    Map<String, Object> evalActualizado = mergeEvaluaciones(persona, request);

                    // Determinar estado global y documentos observados
                    String estadoGlobal  = calcularEstadoGlobal(evalActualizado);
                    List<String> observados = listarObservados(evalActualizado);

                    // Persistir en clientes_v1
                    Mono<Void> guardar = clienteActualizacion.actualizarEvaluacionDocumentos(
                            clienteId, evalActualizado, estadoGlobal, observados);

                    // Recalcular scoreDocumental con los nuevos datos
                    Persona personaActualizada = buildPersonaConNuevaEval(persona, evalActualizado);
                    boolean esFiador = "fiador".equalsIgnoreCase(persona.getTipo());
                    Set<String> docsReq = esFiador ? DOCS_REQUERIDOS_FIADOR : DOCS_REQUERIDOS_TITULAR;
                    ScoreDocumental score = calculadoraScore.calcularScoreDocumental(
                            personaActualizada, docsReq,
                            esFiador ? buildPesosFiador() : CalculadoraScore.PESOS_DOC_TITULAR);

                    // Persistir score en solicitudes
                    Mono<Void> actualizarScore = esFiador
                            ? solicitudActualizacion.actualizarScores(solicitudId, null, score.getValor(), null, null)
                            : solicitudActualizacion.actualizarScores(solicitudId, score.getValor(), null, null, null);

                    return Mono.when(guardar, actualizarScore)
                            .thenReturn(buildResponse(solicitudId, clienteId, score, estadoGlobal));
                });
    }

    /**
     * Combina las evaluaciones previas en Firestore con las nuevas del request.
     * Las nuevas sobreescriben las previas para ese tipoDocumento.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeEvaluaciones(Persona persona, EvaluarDocumentosRequest request) {
        Map<String, Object> merged = new HashMap<>();

        // Copiar evaluaciones previas
        if (persona.getEvaluacionDocumentos() != null) {
            merged.putAll(persona.getEvaluacionDocumentos());
        }

        // Aplicar nuevas evaluaciones del request
        if (request.getEvaluaciones() != null) {
            request.getEvaluaciones().forEach((tipoDoc, item) -> {
                Map<String, Object> evalEntry = new HashMap<>();
                evalEntry.put("estado", item.getEstado());
                evalEntry.put("observaciones", item.getObservaciones() != null ? item.getObservaciones() : "");
                evalEntry.put("fechaEvaluacion", Timestamp.now());
                evalEntry.put("evaluador", request.getEvaluadorId());
                evalEntry.put("evaluadorNombre", request.getEvaluadorNombre());
                merged.put(tipoDoc, evalEntry);
            });
        }
        return merged;
    }

    /**
     * Determina el estado global de validación documental:
     * - Si todos aprobados → 'aprobado'
     * - Si alguno rechazado → 'rechazado'
     * - Si alguno observado → 'observado'
     * - Sino → 'pendiente'
     */
    private String calcularEstadoGlobal(Map<String, Object> evaluaciones) {
        boolean tieneRechazado = false;
        boolean tieneObservado = false;
        boolean tieneAprobado  = false;
        boolean tienePendiente = false;

        for (Object v : evaluaciones.values()) {
            if (!(v instanceof Map<?, ?> m)) continue;
            Object _e = m.get("estado"); String estado = _e != null ? String.valueOf(_e) : "pendiente";
            switch (estado) {
                case "rechazado"  -> tieneRechazado = true;
                case "observado"  -> tieneObservado = true;
                case "aprobado"   -> tieneAprobado  = true;
                default           -> tienePendiente = true;
            }
        }

        if (tieneRechazado)                   return "rechazado";
        if (tieneObservado)                   return "observado";
        if (tieneAprobado && !tienePendiente) return "aprobado";
        return "pendiente";
    }

    private List<String> listarObservados(Map<String, Object> evaluaciones) {
        List<String> observados = new ArrayList<>();
        evaluaciones.forEach((tipo, v) -> {
            if (v instanceof Map<?, ?> m) {
                Object _e = m.get("estado"); String estado = _e != null ? String.valueOf(_e) : "pendiente";
                if ("observado".equals(estado) || "rechazado".equals(estado)) {
                    observados.add(tipo);
                }
            }
        });
        return observados;
    }

    @SuppressWarnings("unchecked")
    private Persona buildPersonaConNuevaEval(Persona persona, Map<String, Object> nuevoEvalDocs) {
        Map<String, Map<String, Object>> tipado = new HashMap<>();
        nuevoEvalDocs.forEach((k, v) -> {
            if (v instanceof Map<?, ?> m) tipado.put(k, (Map<String, Object>) m);
        });
        return Persona.builder()
                .id(persona.getId())
                .tipo(persona.getTipo())
                .documentos(persona.getDocumentos())
                .evaluacionDocumentos(tipado)
                .licenciaDeConducir(persona.getLicenciaDeConducir())
                .tipoDeDocumento(persona.getTipoDeDocumento())
                .estadoResidenciaCE(persona.getEstadoResidenciaCE())
                .build();
    }

    private Map<String, Double> buildPesosFiador() {
        Map<String, Double> p = new HashMap<>();
        p.put("fiadorDniFrente", 10.0); p.put("fiadorDniReverso", 10.0);
        p.put("fiadorLicenciaFrente", 7.5); p.put("fiadorLicenciaReverso", 7.5);
        p.put("fiadorCertificadoLaboral", 20.0); p.put("fiadorReciboServicio", 15.0);
        p.put("fiadorFachada", 10.0);
        return p;
    }

    private EvaluarDocumentosResponse buildResponse(String solicitudId, String clienteId,
                                                     ScoreDocumental score, String estadoGlobal) {
        return EvaluarDocumentosResponse.builder()
                .success(true)
                .clienteId(clienteId)
                .solicitudId(solicitudId)
                .estadoValidacionDocumentos(estadoGlobal)
                .scoreDocumental(score.getValor())
                .docsAprobados(score.getDocsAprobados())
                .docsObservados(score.getDocsObservados())
                .docsRequeridos(score.getDocsRequeridos())
                .estadoPorDocumento(score.getEstadoPorDocumento())
                .mensaje("Evaluación de documentos registrada. Score documental: " + score.getValor())
                .build();
    }
}
