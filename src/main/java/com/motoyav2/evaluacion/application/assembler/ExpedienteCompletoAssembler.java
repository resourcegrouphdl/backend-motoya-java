package com.motoyav2.evaluacion.application.assembler;

import com.motoyav2.evaluacion.application.port.out.ClientePort;
import com.motoyav2.evaluacion.application.port.out.ReferenciasPort;
import com.motoyav2.evaluacion.application.port.out.SolicitudPort;
import com.motoyav2.evaluacion.application.port.out.UsuarioPort;
import com.motoyav2.evaluacion.application.port.out.VehiculoPort;
import com.motoyav2.evaluacion.domain.model.AlertaEntrevista;
import com.motoyav2.evaluacion.domain.model.EntrevistaCompleta;
import com.motoyav2.evaluacion.domain.model.ExpedienteSeed;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.model.riesgo.FlagRiesgo;
import com.motoyav2.evaluacion.domain.model.riesgo.PerfilRiesgo;
import com.motoyav2.evaluacion.domain.model.scoring.CapacidadDePagoCalculo;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreDocumental;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreResult;
import com.motoyav2.evaluacion.domain.service.AnalizadorRiesgo;
import com.motoyav2.evaluacion.domain.service.CalculadoraScore;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.AsesorDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.AlertaEntrevistaDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.ClienteCompletoDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.EntrevistaCompletoDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.ReferenciaCompletoDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.SolicitudDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.VehiculoCompletoDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse.VendedorSimpleDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.PerfilRiesgoDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.PerfilRiesgoDto.FlagRiesgoDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ScoreResultDto;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseSolicitud;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.VendedorFirebase;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper.SolicitudMaper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseSolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembler que orquesta la lectura paralela de todas las colecciones Firebase
 * y construye el ExpedienteCompletoResponse con scores y perfil de riesgo calculados.
 *
 * Lee directamente de: solicitudes, clientes_v1, vehiculos, referencias, usuarios.
 * No depende de evaluacionDeCredito — es el canal de lectura principal del frontend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpedienteCompletoAssembler {

    private final FirebaseSolicitudRepository solicitudRepository;
    private final ClientePort clientePort;
    private final VehiculoPort vehiculoPort;
    private final ReferenciasPort referenciasPort;
    private final UsuarioPort usuarioPort;
    private final CalculadoraScore calculadoraScore;
    private final AnalizadorRiesgo analizadorRiesgo;

    public Mono<ExpedienteCompletoResponse> ensamblar(String solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(this::ensamblarDesde);
    }

    private Mono<ExpedienteCompletoResponse> ensamblarDesde(FirebaseSolicitud sol) {
        String titularId = sol.getTitularId();
        String fiadorId  = sol.getFiadorId();
        String vehiculoId = sol.getVehiculoId();
        List<String> refsIds = sol.getReferenciasIds() != null ? sol.getReferenciasIds() : List.of();

        Mono<Persona> titularMono = clientePort.buscarPorId(titularId);
        Mono<Persona> fiadorMono  = fiadorId != null && !fiadorId.isBlank()
                ? clientePort.buscarPorId(fiadorId).defaultIfEmpty(Persona.builder().build())
                : Mono.just(Persona.builder().build());
        Mono<Vehiculo> vehiculoMono = vehiculoPort.buscarPorId(vehiculoId);
        Mono<List<ReferenciasDelTitular>> refsMono = referenciasPort
                .buscarPorIds(refsIds).collectList();
        Mono<AsesorDto> asesorMono = sol.getAsesorAsignadoId() != null
                ? usuarioPort.buscarPorId(sol.getAsesorAsignadoId()).defaultIfEmpty(null)
                : Mono.justOrEmpty((AsesorDto) null);

        return Mono.zip(titularMono, fiadorMono, vehiculoMono, refsMono, asesorMono)
                .map(t -> {
                    Persona titular  = t.getT1();
                    Persona fiadorRaw = t.getT2();
                    Persona fiador   = fiadorRaw.getId() != null ? fiadorRaw : null;
                    Vehiculo vehiculo = t.getT3();
                    List<ReferenciasDelTitular> refs = t.getT4();
                    AsesorDto asesor = t.getT5();

                    String montoCuotaStr = sol.getMontoCuota() != null ? sol.getMontoCuota().toString() : null;

                    // Calcular scores (Domain Services puros)
                    ScoreResult scores = calculadoraScore.calcularTodo(titular, fiador, refs, montoCuotaStr);

                    // Analizar riesgo (Domain Service puro)
                    PerfilRiesgo perfil = analizadorRiesgo.analizar(titular, fiador, refs, scores);

                    log.info("Expediente completo ensamblado — solicitud: {}, scoreFinal: {}, riesgo: {}",
                            sol.getCodigoDeSolicitud(), scores.getScoreFinal(), perfil.getNivelGeneral());

                    return buildResponse(sol, titular, fiador, vehiculo, refs, asesor, scores, perfil);
                });
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private ExpedienteCompletoResponse buildResponse(FirebaseSolicitud sol,
                                                      Persona titular, Persona fiador,
                                                      Vehiculo vehiculo,
                                                      List<ReferenciasDelTitular> refs,
                                                      AsesorDto asesor,
                                                      ScoreResult scores, PerfilRiesgo perfil) {
        boolean datosCompletos = titular != null && titular.getId() != null
                && vehiculo != null && vehiculo.getId() != null
                && !refs.isEmpty();

        return ExpedienteCompletoResponse.builder()
                .solicitudId(sol.getFormularioId())
                .solicitud(buildSolicitudDto(sol))
                .titular(buildClienteDto(titular))
                .fiador(fiador != null ? buildClienteDto(fiador) : null)
                .vehiculo(buildVehiculoDto(vehiculo))
                .referencias(refs.stream().map(this::buildReferenciaDto).toList())
                .evaluacionEntrevista(buildEntrevistaDto(titular != null ? titular.getEvaluacionEntrevista() : null))
                .datosCompletos(datosCompletos)
                .asesorAsignado(asesor)
                .scores(buildScoreDto(scores))
                .perfilRiesgo(buildPerfilDto(perfil))
                .build();
    }

    private SolicitudDto buildSolicitudDto(FirebaseSolicitud sol) {
        VendedorFirebase v = sol.getVendedor();
        return SolicitudDto.builder()
                .id(sol.getFormularioId())
                .numeroSolicitud(resolverNumeroSolicitud(sol))
                .estado(sol.getEstado())
                .prioridad(sol.getPrioridad())
                .titularId(sol.getTitularId())
                .fiadorId(sol.getFiadorId())
                .vehiculoId(sol.getVehiculoId())
                .referenciasIds(sol.getReferenciasIds())
                .precioCompraMoto(sol.getPrecioCompraMoto())
                .inicial(sol.getInicial())
                .montoCuota(sol.getMontoCuota())
                .plazoQuincenas(sol.getPlazoQuincenas())
                .datosFinancieros(sol.getDatosFinancieros())
                .vendedor(v != null ? VendedorSimpleDto.builder()
                        .id(v.getId())
                        .nombre(v.getNombreVendedor())
                        .tienda(v.getTienda() != null && !v.getTienda().isEmpty() ? v.getTienda().getFirst() : null)
                        .build() : null)
                .mensajeOpcional(sol.getMensajeOpcional())
                .asesorAsignadoId(sol.getAsesorAsignadoId())
                .scoreDocumental(sol.getScoreDocumental())
                .scoreGarantes(sol.getScoreGarantes())
                .scoreEntrevista(sol.getScoreEntrevista())
                .scoreFinal(sol.getScoreFinal())
                .decisionFinal(sol.getDecisionFinal())
                .montoAprobado(sol.getMontoAprobado())
                .motivoRechazo(sol.getMotivoRechazo())
                .motivoDecision(sol.getMotivoDecision())
                .condicionesAprobacion(sol.getCondicionesAprobacion())
                .certificadoGenerado(sol.getCertificadoGenerado())
                .urlCertificado(resolverUrlCertificado(sol))
                .contratoGenerado(sol.getContratoGenerado())
                .urlContrato(sol.getUrlContrato())
                .observacionesGenerales(sol.getObservacionesGenerales())
                .createdAt(sol.getCreatedAt() != null ? sol.getCreatedAt().toString() : null)
                .updatedAt(sol.getUpdatedAt() != null ? sol.getUpdatedAt().toString() : null)
                .build();
    }

    private ClienteCompletoDto buildClienteDto(Persona p) {
        if (p == null) return null;
        Map<String, Object> archivosMap = new HashMap<>();
        if (p.getDocumentos() != null) {
            p.getDocumentos().forEach(d -> {
                if (d.getTipoDocumento() != null) archivosMap.put(d.getTipoDocumento(), d.getUrl());
            });
        }
        Map<String, Object> evalDocsMap = p.getEvaluacionDocumentos() != null
                ? new HashMap<>(p.getEvaluacionDocumentos()) : null;

        return ClienteCompletoDto.builder()
                .id(p.getId())
                .tipo(p.getTipo())
                .nombres(p.getNombres())
                .apellidoPaterno(p.getApellidoPaterno())
                .apellidoMaterno(p.getApellidoMaterno())
                .nombreCompleto(p.getNombreCompleto())
                .documentType(p.getTipoDeDocumento())
                .documentNumber(p.getNumeroDeDocumento())
                .nacionalidad(p.getNacionalidad())
                .sexo(p.getSexo())
                .fechaNacimiento(p.getFechaNacimiento())
                .estadoCivil(p.getEstadoCivil())
                .cargasFamiliares(p.getCargasFamiliares())
                .cargasFamiliaresNum(p.getCargasFamiliaresNum())
                .email(p.getEmail())
                .telefono1(p.getTelefono1())
                .telefono2(p.getTelefono2())
                .departamento(p.getDepartamento())
                .provincia(p.getProvincia())
                .distrito(p.getDistrito())
                .direccion(p.getDireccion())
                .tipoDeVivienda(p.getTipoDeVivienda())
                .antiguedadDomiciliaria(p.getAntiguedadDomiciliaria())
                .ocupacion(p.getOcupacion())
                .tipoTrabajo(p.getTipoTrabajo())
                .nombreEmpresa(p.getNombreEmpresa())
                .ingresoMensual(p.getIngresoMensual())
                .ingresoMensualNum(p.getIngresoMensualNum())
                .rangoIngresos(p.getRangoIngresos())
                .licenciaDeConducir(p.getLicenciaDeConducir())
                .perfilSentinel(p.getPerfilSentinel())
                .totalDeudaBancos(p.getTotalDeudaBancos())
                .totalOtrasDeudas(p.getTotalOtrasDeudas())
                .tienePapeletasPendientes(p.getTienePapeletasPendientes())
                .totalDeudaPapeletas(p.getTotalDeudaPapeletas())
                .estadoValidacionDocumentos(p.getEstadoValidacionDocumentos())
                .datosVerificados(p.getDatosVerificados())
                .observacionesEvaluador(p.getObservacionesEvaluador())
                .archivos(archivosMap)
                .evaluacionDocumentos(evalDocsMap)
                .createdAt(p.getCreadoEn())
                .updatedAt(p.getActualizadoEn())
                .build();
    }

    private VehiculoCompletoDto buildVehiculoDto(Vehiculo v) {
        if (v == null) return null;
        return VehiculoCompletoDto.builder()
                .id(v.getId())
                .marca(v.getMarca())
                .modelo(v.getModelo())
                .anio(v.getAnio())
                .color(v.getColor())
                .createdAt(v.getCreadoEn())
                .build();
    }

    private ReferenciaCompletoDto buildReferenciaDto(ReferenciasDelTitular r) {
        return ReferenciaCompletoDto.builder()
                .id(r.getId())
                .numero(r.getNumero())
                .nombre(r.getNombre())
                .apellidos(r.getApellidos())
                .nombreCompleto(r.getNombreCompleto())
                .telefono(r.getTelefono())
                .parentesco(r.getParentesco())
                .estadoVerificacion(r.getEstadoVerificacion())
                .resultadoContacto(r.getResultadoContacto())
                .scoreVerificacion(r.getScoreDeVerificacionNum())
                .calificacion(r.getCalificacion())
                .observaciones(r.getObservaciones())
                .actitudDuranteContacto(r.getActitudDuranteContacto())
                .rechazada(r.getRechazada())
                .build();
    }

    private EntrevistaCompletoDto buildEntrevistaDto(EntrevistaCompleta e) {
        if (e == null) return null;
        return EntrevistaCompletoDto.builder()
                .solicitudId(e.getSolicitudId())
                .entrevistadorId(e.getEntrevistadorId())
                .entrevistadorNombre(e.getEntrevistadorNombre())
                .modalidad(e.getModalidad())
                .puntualidad(e.getPuntualidad())
                .presentacionPersonal(e.getPresentacionPersonal())
                .actitudColaboracion(e.getActitudColaboracion())
                .coherenciaRespuestas(e.getCoherenciaRespuestas())
                .nivelConfianza(e.getNivelConfianza())
                .observacionesCliente(e.getObservacionesCliente())
                .observacionesDomicilio(e.getObservacionesDomicilio())
                .observacionesCapacidadPago(e.getObservacionesCapacidadPago())
                .hallazgosPositivos(e.getHallazgosPositivos())
                .hallazgosNegativos(e.getHallazgosNegativos())
                .alertas(e.getAlertas() != null
                        ? e.getAlertas().stream().map(a -> AlertaEntrevistaDto.builder()
                            .tipo(a.getTipo()).descripcion(a.getDescripcion())
                            .severidad(a.getSeveridad()).timestamp(a.getTimestamp()).build()).toList()
                        : List.of())
                .scoreEntrevista(e.getScoreEntrevista())
                .recomendacion(e.getRecomendacion())
                .motivoRecomendacion(e.getMotivoRecomendacion())
                .esBorrador(e.getEsBorrador())
                .build();
    }

    private ScoreResultDto buildScoreDto(ScoreResult s) {
        ScoreDocumental doc = s.getScoreDocumental();
        CapacidadDePagoCalculo cap = s.getCapacidadDePago();
        return ScoreResultDto.builder()
                .scoreFinal(s.getScoreFinal())
                .scoreDocumental(doc.getValor())
                .scoreGarantes(s.getScoreGarantes() != null ? s.getScoreGarantes().getValor() : null)
                .scoreEntrevista(s.getScoreEntrevista().getValor())
                .scoreReferencias(s.getScoreReferencias().getValor())
                .tieneFiador(s.isTieneFiador())
                .descripcionPonderacion(s.getDescripcionPonderacion())
                .completitudDocumental(doc.getCompletitud())
                .aprobacionDocumental(doc.getAprobacion())
                .docsSubidos(doc.getDocsSubidos())
                .docsAprobados(doc.getDocsAprobados())
                .docsRequeridos(doc.getDocsRequeridos())
                .estadoPorDocumento(doc.getEstadoPorDocumento())
                .licenciaVigente(doc.isLicenciaVigente())
                .entrevistaRealizada(s.getScoreEntrevista().isEntrevistaRealizada())
                .recomendacionEntrevista(s.getScoreEntrevista().getRecomendacion())
                .alertasCriticas(s.getScoreEntrevista().getAlertasCriticas())
                .alertasAltas(s.getScoreEntrevista().getAlertasAltas())
                .referenciasVerificadas(s.getScoreReferencias().getVerificadas())
                .referenciasRechazadas(s.getScoreReferencias().getRechazadas())
                .ingresoMensualEstimado(cap.getIngresoMensualEstimado())
                .cuotaMensual(cap.getCuotaMensual())
                .ratioCuotaIngreso(cap.getRatioCuotaIngreso())
                .cumpleRatioCuota(cap.isCumpleRatio())
                .nivelCapacidadPago(cap.getNivelCapacidad())
                .ingresoEstimado(cap.isIngresoEstimado())
                .build();
    }

    private PerfilRiesgoDto buildPerfilDto(PerfilRiesgo p) {
        return PerfilRiesgoDto.builder()
                .nivelGeneral(p.getNivelGeneral().name())
                .totalFlags(p.getTotalFlags())
                .flagsCriticos(p.getFlagsCriticos())
                .flagsAltos(p.getFlagsAltos())
                .flagsMedios(p.getFlagsMedios())
                .recomendacionAutomatica(p.recomendacionAutomatica())
                .flags(p.getFlags().stream().map(f -> FlagRiesgoDto.builder()
                        .tipo(f.getTipo().name())
                        .severidad(f.getSeveridad().name())
                        .descripcion(f.getDescripcion())
                        .origen(f.getOrigen())
                        .build()).toList())
                .build();
    }

    // ── Normalizaciones para quirks del contrato ─────────────────────────────

    private String resolverNumeroSolicitud(FirebaseSolicitud sol) {
        if (sol.getNumeroSolicitud() != null) return sol.getNumeroSolicitud();
        return sol.getCodigoDeSolicitud();
    }

    private String resolverUrlCertificado(FirebaseSolicitud sol) {
        if (sol.getUrlCertificado() != null) return sol.getUrlCertificado();
        return sol.getUrlDelCertificadoGenerado(); // alias normalización
    }
}
