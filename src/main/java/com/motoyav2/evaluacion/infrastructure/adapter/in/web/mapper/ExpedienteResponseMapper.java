package com.motoyav2.evaluacion.infrastructure.adapter.in.web.mapper;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente.*;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExpedienteResponseMapper {

    private ExpedienteResponseMapper() {}

    public static ExpedienteDto toExpedienteDto(EvaluacionCreditoDocument doc) {
        List<EtapaDocument> etapas = doc.getEtapas();
        int etapasCompletadas = 0;
        int etapasTotales = 0;
        String etapaActualNombre = null;

        if (etapas != null) {
            etapasTotales = etapas.size();
            for (EtapaDocument e : etapas) {
                if ("COMPLETADA".equals(e.getEstado())) {
                    etapasCompletadas++;
                }
                if ("EN_PROCESO".equals(e.getEstado())) {
                    etapaActualNombre = e.getNombre();
                }
            }
        }

        // Find TITULAR and FIADOR entrevistas
        EvaluacionEntrevistaDto evaluacionTitular = null;
        EvaluacionEntrevistaDto evaluacionFiador = null;
        if (doc.getEntrevistas() != null) {
            for (EntrevistaDocument ent : doc.getEntrevistas()) {
                if ("TITULAR".equalsIgnoreCase(ent.getTipoPersona())) {
                    evaluacionTitular = toEntrevistaDto(ent);
                } else if ("FIADOR".equalsIgnoreCase(ent.getTipoPersona())) {
                    evaluacionFiador = toEntrevistaDto(ent);
                }
            }
        }

        Long tiempoTranscurrido = calcularTiempoTranscurrido(doc.getCreadoEn());

        return ExpedienteDto.builder()
                .evaluacionId(doc.getCodigoDeSolicitud())
                .numeroEvaluacion(doc.getNumeroEvaluacion())
                .codigoSolicitud(doc.getCodigoDeSolicitud())
                .solicitudFirebaseId(doc.getSolicitudFirebaseId())
                .estadoActual(doc.getEstado())
                .etapaActual(etapaActualNombre != null ? etapaActualNombre : doc.getEtapa())
                .prioridad(doc.getPrioridad())
                .progreso(ProgresoDto.builder()
                        .porcentaje(doc.getProgresoPorcentaje())
                        .etapasCompletadas(etapasCompletadas)
                        .etapasTotales(etapasTotales)
                        .descripcion(buildDescripcionProgreso(etapaActualNombre, doc.getEtapa()))
                        .build())
                .titular(toPersonaDto(doc.getTitular()))
                .fiador(doc.getFiador() != null ? toPersonaDto(doc.getFiador()) : null)
                .vehiculo(toVehiculoDto(doc.getVehiculo()))
                .referencias(toReferenciasDtoList(doc.getReferencias()))
                .datosFinancieros(toFinancierosDto(doc.getFinanciamiento()))
                .evaluacionTitular(evaluacionTitular)
                .evaluacionFiador(evaluacionFiador)
                .etapas(toEtapasDtoList(doc.getEtapas()))
                .scores(toScoresDto(doc))
                .alertas(toAlertasDtoList(doc.getAlertas()))
                .documentos(buildAllDocumentos(doc))
                .vendedor(VendedorDto.builder()
                        .uid(doc.getVendedorId())
                        .nombre(doc.getVendedorNombre())
                        .telefono(doc.getVendedorTelefono())
                        .email(doc.getVendedorEmail())
                        .build())
                .tienda(TiendaDto.builder()
                        .id(doc.getTiendaId())
                        .nombre(doc.getTiendaNombre())
                        .codigo(doc.getTiendaCodigo())
                        .direccion(null)
                        .build())
                .fechaCreacion(doc.getCreadoEn())
                .fechaUltimaActualizacion(doc.getActualizadoEn())
                .tiempoTranscurrido(tiempoTranscurrido)
                .asignadoA(doc.getAsignadoA())
                .nombreEvaluador(doc.getNombreEvaluador())
                .decisionFinal(doc.getDecision())
                .build();
    }

    private static PersonaDto toPersonaDto(PersonaDocument p) {
        if (p == null) return null;

        UbicacionGpsDto ubicacionGps = toUbicacionGps(p.getUbicacionGpsLat(), p.getUbicacionGpsLng());
        UbicacionGpsDto ubicacionTrabajo = toUbicacionGps(p.getUbicacionDelTrabajoLat(), p.getUbicacionDelTrabajoLng());

        Map<String, String> archivos = buildArchivosMap(p.getDocumentos());

        return PersonaDto.builder()
                .id(p.getId())
                .nombres(p.getNombres())
                .apellidoPaterno(p.getApellidoPaterno())
                .apellidoMaterno(p.getApellidoMaterno())
                .nombreCompleto(p.getNombreCompleto())
                .sexo(p.getSexo())
                .fechaNacimiento(p.getFechaNacimiento())
                .edad(toInteger(p.getEdad()))
                .estadoCivil(p.getEstadoCivil())
                .cargasFamiliares(toInteger(p.getCargasFamiliares()))
                .documentType(p.getTipoDeDocumento())
                .documentNumber(p.getNumeroDeDocumento())
                .nacionalidad(p.getNacionalidad())
                .email(p.getEmail())
                .telefono1(p.getTelefono1())
                .telefono2(p.getTelefono2())
                .departamento(p.getDepartamento())
                .provincia(p.getProvincia())
                .distrito(p.getDistrito())
                .direccion(p.getDireccion())
                .direccionCompleta(p.getDireccionCompleta())
                .tipoVivienda(p.getTipoDeVivienda())
                .antiguedadDomiciliaria(p.getAntiguedadDomiciliaria())
                .referenciaUbicacion(p.getReferenciaUbicacion())
                .ubicacionGPS(ubicacionGps)
                .ocupacion(p.getOcupacion())
                .tipoTrabajo(p.getTipoTrabajo())
                .nombreEmpresa(p.getNombreEmpresa())
                .ubicacionGPSTrabajo(ubicacionTrabajo)
                .antiguedadTrabajo(p.getAntiguedadDelTrabajo())
                .ingresoMensual(toDouble(p.getIngresoMensual()))
                .rangoIngresos(p.getRangoIngresos())
                .licenciaConducir(p.getLicenciaDeConducir())
                .numeroLicencia(p.getNumeroDeLicencia())
                .vencimientoLicencia(p.getVencimientoLicencia())
                .licenciaVigente(toBoolean(p.getLicenciaVigente()))
                .archivos(archivos.isEmpty() ? null : archivos)
                .build();
    }

    private static UbicacionGpsDto toUbicacionGps(String lat, String lng) {
        if (lat == null && lng == null) return null;
        return UbicacionGpsDto.builder()
                .lat(toDouble(lat))
                .lng(toDouble(lng))
                .build();
    }

    private static Map<String, String> buildArchivosMap(List<DocumentoDocument> docs) {
        Map<String, String> archivos = new LinkedHashMap<>();
        if (docs == null) return archivos;
        for (DocumentoDocument d : docs) {
            if (d.getTipoDocumento() != null && d.getUrl() != null) {
                // La clave viene directamente del Map<String,String> archivos de Firebase
                // (ej: "dniFrontal", "dniReverso", "licencia", "fachadaDomicilio", "comprobanteDomicilio")
                // Se usa tal cual, sin transformar
                archivos.put(d.getTipoDocumento(), d.getUrl());
            }
        }
        return archivos;
    }

    private static VehiculoDto toVehiculoDto(VehiculoDocument v) {
        if (v == null) return null;
        return VehiculoDto.builder()
                .id(v.getId())
                .marca(v.getMarca())
                .modelo(v.getModelo())
                .anio(v.getAnio())
                .color(v.getColor())
                .cilindrada(toInteger(v.getCilindrada()))
                .descripcionCompleta(v.getDescripcionCompleta())
                .precioReferencial(toDouble(v.getPrecioReferencial()))
                .build();
    }

    private static List<ReferenciaDto> toReferenciasDtoList(List<ReferenciaDocument> refs) {
        if (refs == null) return List.of();
        return refs.stream().map(ExpedienteResponseMapper::toReferenciaDto).toList();
    }

    private static ReferenciaDto toReferenciaDto(ReferenciaDocument r) {
        return ReferenciaDto.builder()
                .id(r.getId())
                .numero(r.getNumero())
                .nombre(r.getNombre())
                .apellidos(r.getApellidos())
                .nombreCompleto(r.getNombreCompleto())
                .telefono(r.getTelefono())
                .parentesco(r.getParentesco())
                .verificada(r.getVerificada())
                .fechaVerificacion(r.getFechaDeVerificacion())
                .verificadoPor(r.getVerificadoPor())
                .resultadoVerificacion(r.getResultadoDeVerificacion())
                .observaciones(r.getObservaciones())
                .scoreVerificacion(toDouble(r.getScoreDeVerificacion()))
                .build();
    }

    private static DatosFinancierosDto toFinancierosDto(FinanciamientoDocument f) {
        if (f == null) return null;

        Double soat = toDouble(f.getSoat());
        Double notariales = toDouble(f.getCostosNotariales());
        Double soatCostosNotariales = null;
        if (soat != null || notariales != null) {
            soatCostosNotariales = (soat != null ? soat : 0.0) + (notariales != null ? notariales : 0.0);
        }

        return DatosFinancierosDto.builder()
                .montoVehiculo(toDouble(f.getMontoDelVehiculo()))
                .soatCostosNotariales(soatCostosNotariales)
                .costoTotal(toDouble(f.getCostoTotal()))
                .inicialOriginal(toDouble(f.getInicialOriginal()))
                .montoFinanciarOriginal(toDouble(f.getMontoAFinanciarOriginal()))
                .numeroCuotasQuincenales(toInteger(f.getNumeroCuotasQuincenales()))
                .montoCuotaQuincenal(toDouble(f.getMontoCuotaQuincenal()))
                .inicialAjustada(toDouble(f.getInicialAjustada()))
                .montoFinanciarAjustado(toDouble(f.getMontoAFinanciarAjustado()))
                .montoCuotaAjustada(toDouble(f.getMontoCuotaAjustada()))
                .porcentajeInicial(toDouble(f.getPorcentajeInicial()))
                .relacionCuotaIngreso(toDouble(f.getRelacionCuotaIngreso()))
                .capacidadPago(f.getCapacidadDePago())
                .build();
    }

    private static EvaluacionEntrevistaDto toEntrevistaDto(EntrevistaDocument e) {
        if (e == null) return null;
        return EvaluacionEntrevistaDto.builder()
                .actitud(e.getActitud())
                .disposicion(e.getDisposicion())
                .claridad(e.getClaridad())
                .estabilidadLaboral(e.getEstabilidadLaboral())
                .estabilidadDomiciliaria(e.getEstabilidadDomiciliaria())
                .historicoCrediticio(e.getHistoricoCrediticio())
                .ingresoVerificable(e.getIngresoVerificable())
                .comprobanteIngresos(e.getComprobanteIngresos())
                .gastosMensuales(toDouble(e.getGastosMensuales()))
                .capacidadPagoCalculada(toDouble(e.getCapacidadPagoCalculada()))
                .scoreTotal(toInteger(e.getScoreTotal()))
                .observaciones(e.getObservaciones())
                .recomendacion(e.getRecomendacion())
                .fechaEntrevista(e.getFechaEntrevista())
                .duracion(e.getDuracionMinutos())
                .realizadoPor(e.getNombreEntrevistador())
                .build();
    }

    private static List<EtapaDto> toEtapasDtoList(List<EtapaDocument> etapas) {
        if (etapas == null) return List.of();
        return etapas.stream().map(ExpedienteResponseMapper::toEtapaDto).toList();
    }

    private static EtapaDto toEtapaDto(EtapaDocument e) {
        return EtapaDto.builder()
                .numero(toInteger(e.getNumero()))
                .nombre(e.getNombre())
                .descripcion(e.getDescripcion())
                .estado(e.getEstado())
                .fechaInicio(e.getFechaInicio())
                .fechaFin(e.getFechaFin())
                .completadoPor(e.getCompletadoPor())
                .observaciones(e.getObservaciones())
                .build();
    }

    private static ScoresDto toScoresDto(EvaluacionCreditoDocument doc) {
        return ScoresDto.builder()
                .documental(toInteger(doc.getScoreDocumental()))
                .referencias(toInteger(doc.getScoreReferencias()))
                .crediticio(toInteger(doc.getScoreCrediticio()))
                .ingresos(toInteger(doc.getScoreIngresos()))
                .entrevistaTitular(toInteger(doc.getScoreEntrevistaTitular()))
                .entrevistaFiador(toInteger(doc.getScoreEntrevistaFiador()))
                .scoreFinal(toInteger(doc.getScoreFinal()))
                .build();
    }

    private static List<AlertaDto> toAlertasDtoList(List<AlertaDocument> alertas) {
        if (alertas == null) return List.of();
        return alertas.stream().map(ExpedienteResponseMapper::toAlertaDto).toList();
    }

    private static AlertaDto toAlertaDto(AlertaDocument a) {
        return AlertaDto.builder()
                .tipo(a.getTipo())
                .mensaje(a.getMensaje())
                .descripcion(a.getDescripcion())
                .fechaCreacion(a.getCreadoEn())
                .resuelta(a.getResuelta())
                .fechaResolucion(a.getFechaDeResolucion())
                .build();
    }

    private static List<DocumentoDto> buildAllDocumentos(EvaluacionCreditoDocument doc) {
        List<DocumentoDto> todos = new ArrayList<>();

        // 1. Documentos del nivel top (evaluacionDeCredito.documentos)
        if (doc.getDocumentos() != null) {
            for (DocumentoDocument d : doc.getDocumentos()) {
                todos.add(toDocumentoDto(d));
            }
        }

        // 2. Documentos embebidos del titular
        if (doc.getTitular() != null && doc.getTitular().getDocumentos() != null) {
            for (DocumentoDocument d : doc.getTitular().getDocumentos()) {
                if (!existeDocumentoPorUrl(todos, d.getUrl())) {
                    todos.add(toDocumentoDtoConTipoPersona(d, "TITULAR"));
                }
            }
        }

        // 3. Documentos embebidos del fiador
        if (doc.getFiador() != null && doc.getFiador().getDocumentos() != null) {
            for (DocumentoDocument d : doc.getFiador().getDocumentos()) {
                if (!existeDocumentoPorUrl(todos, d.getUrl())) {
                    todos.add(toDocumentoDtoConTipoPersona(d, "FIADOR"));
                }
            }
        }

        return todos;
    }

    private static boolean existeDocumentoPorUrl(List<DocumentoDto> lista, String url) {
        if (url == null) return false;
        return lista.stream().anyMatch(d -> url.equals(d.getUrl()));
    }

    private static DocumentoDto toDocumentoDto(DocumentoDocument d) {
        return DocumentoDto.builder()
                .id(d.getId())
                .tipo(d.getTipoDocumento())
                .nombre(d.getNombre())
                .url(d.getUrl())
                .tipoPersona(d.getTipoPersona())
                .validado(d.getValidado())
                .observaciones(d.getObservaciones())
                .fechaSubida(d.getCreadoEn())
                .build();
    }

    private static DocumentoDto toDocumentoDtoConTipoPersona(DocumentoDocument d, String tipoPersona) {
        return DocumentoDto.builder()
                .id(d.getId())
                .tipo(d.getTipoDocumento())
                .nombre(d.getNombre() != null ? d.getNombre() : d.getTipoDocumento())
                .url(d.getUrl())
                .tipoPersona(d.getTipoPersona() != null ? d.getTipoPersona() : tipoPersona)
                .validado(d.getValidado())
                .observaciones(d.getObservaciones())
                .fechaSubida(d.getCreadoEn())
                .build();
    }

    private static String buildDescripcionProgreso(String etapaActualNombre, String etapa) {
        String nombre = etapaActualNombre != null ? etapaActualNombre : etapa;
        if (nombre == null) return null;
        String readable = nombre.replace("_", " ").toLowerCase();
        return readable.substring(0, 1).toUpperCase() + readable.substring(1) + " en proceso";
    }

    private static Long calcularTiempoTranscurrido(String creadoEn) {
        if (creadoEn == null) return null;
        try {
            Instant creacion = Instant.parse(creadoEn);
            return Duration.between(creacion, Instant.now()).toMinutes();
        } catch (Exception e) {
            return null;
        }
    }

    private static Double toDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value).doubleValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean toBoolean(String value) {
        if (value == null || value.isBlank()) return null;
        return Boolean.parseBoolean(value);
    }
}
