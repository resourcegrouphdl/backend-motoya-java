package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.port.in.VerificarReferenciaUseCase;
import com.motoyav2.evaluacion.application.port.out.ReferenciaActualizacionPort;
import com.motoyav2.evaluacion.application.port.out.ReferenciasPort;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreReferencias;
import com.motoyav2.evaluacion.domain.service.CalculadoraScore;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias.VerificarReferenciaRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias.VerificarReferenciaResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseSolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificarReferenciaService implements VerificarReferenciaUseCase {

    private final FirebaseSolicitudRepository solicitudRepository;
    private final ReferenciasPort referenciasPort;
    private final ReferenciaActualizacionPort referenciaActualizacion;
    private final CalculadoraScore calculadoraScore;

    @Override
    public Mono<VerificarReferenciaResponse> ejecutar(String solicitudId,
                                                       String referenciaId,
                                                       VerificarReferenciaRequest request) {
        // 1. Actualizar la referencia en Firestore
        Map<String, Object> campos = buildCampos(request);
        Mono<Void> actualizar = referenciaActualizacion.actualizarVerificacion(referenciaId, campos);

        // 2. Leer solicitud para obtener todas las referenciasIds y recalcular score
        return actualizar.then(
            solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(sol -> {
                    List<String> refsIds = sol.getReferenciasIds() != null ? sol.getReferenciasIds() : List.of();

                    return referenciasPort.buscarPorIds(refsIds)
                            .collectList()
                            .map(refs -> {
                                // La referencia recién actualizada aún tiene datos viejos en memoria;
                                // la sustituimos con los datos del request para el cálculo
                                List<ReferenciasDelTitular> refsActualizadas = refs.stream()
                                        .map(r -> r.getId() != null && r.getId().equals(referenciaId)
                                                ? aplicarActualizacion(r, request) : r)
                                        .toList();

                                ScoreReferencias score = calculadoraScore.calcularScoreReferencias(refsActualizadas);

                                ReferenciasDelTitular refActualizada = refsActualizadas.stream()
                                        .filter(r -> referenciaId.equals(r.getId()))
                                        .findFirst()
                                        .orElse(null);

                                log.info("Referencia {} verificada — scoreReferencias recalculado: {}",
                                        referenciaId, score.getValor());

                                return VerificarReferenciaResponse.builder()
                                        .success(true)
                                        .solicitudId(solicitudId)
                                        .referenciaId(referenciaId)
                                        .numeroReferencia(refActualizada != null ? refActualizada.getNumero() : null)
                                        .estadoVerificacion(request.getEstadoVerificacion())
                                        .scoreVerificacion(request.getScoreVerificacion() != null ? request.getScoreVerificacion() : 0)
                                        .scoreReferencias(score.getValor())
                                        .referenciasVerificadas(score.getVerificadas())
                                        .referenciasRechazadas(score.getRechazadas())
                                        .totalReferencias(score.getTotalReferencias())
                                        .mensaje("Referencia verificada. Score referencias: " + score.getValor())
                                        .build();
                            });
                })
        );
    }

    private Map<String, Object> buildCampos(VerificarReferenciaRequest req) {
        Map<String, Object> map = new HashMap<>();
        if (req.getEstadoVerificacion() != null)     map.put("estadoVerificacion",     req.getEstadoVerificacion());
        if (req.getResultadoContacto() != null)      map.put("resultadoContacto",      req.getResultadoContacto());
        if (req.getScoreVerificacion() != null)      map.put("scoreVerificacion",       req.getScoreVerificacion());
        if (req.getScoreMaximo() != null)            map.put("scoreMaximo",             req.getScoreMaximo());
        else                                          map.put("scoreMaximo",             100.0);
        if (req.getCalificacion() != null)           map.put("calificacion",            req.getCalificacion());
        if (req.getActitudDuranteContacto() != null) map.put("actitudDuranteContacto",  req.getActitudDuranteContacto());
        if (req.getObservaciones() != null)          map.put("observaciones",           req.getObservaciones());
        if (req.getRechazada() != null)              map.put("rechazada",               req.getRechazada());
        if (req.getRespuestasPreguntas() != null)    map.put("respuestasPreguntas",     req.getRespuestasPreguntas());
        return map;
    }

    /** Proyecta los datos del request sobre la instancia en memoria para el cálculo inmediato. */
    private ReferenciasDelTitular aplicarActualizacion(ReferenciasDelTitular ref, VerificarReferenciaRequest req) {
        return ReferenciasDelTitular.builder()
                .id(ref.getId())
                .numero(ref.getNumero())
                .nombre(ref.getNombre())
                .apellidos(ref.getApellidos())
                .telefono(ref.getTelefono())
                .parentesco(ref.getParentesco())
                .estadoVerificacion(req.getEstadoVerificacion())
                .resultadoContacto(req.getResultadoContacto())
                .scoreDeVerificacionNum(req.getScoreVerificacion())
                .calificacion(req.getCalificacion())
                .actitudDuranteContacto(req.getActitudDuranteContacto())
                .observaciones(req.getObservaciones())
                .rechazada(Boolean.TRUE.equals(req.getRechazada()))
                .build();
    }
}
