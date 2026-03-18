package com.motoyav2.evaluacion.infrastructure.adapter.in.web.response;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.model.*;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class ExpedienteCompletoResponse {
    SolicitudResponse solicitud;
    ClienteResponse titular;
    ClienteResponse fiador;
    VehiculoResponse vehiculo;
    List<ReferenciaResponse> referencias;
    Boolean datosCompletos;
    AsesorResponse asesorAsignado;

    // ── Nested response types ──────────────────────────────────────────────

    @Value @Builder
    public static class SolicitudResponse {
        String id;
        String numeroSolicitud;
        String estado;
        String prioridad;
        String titularId;
        String fiadorId;
        String vehiculoId;
        List<String> referenciasIds;
        Double precioCompraMoto;
        Double inicial;
        Double montoCuota;
        Integer plazoQuincenas;
        DatosFinancierosResponse datosFinancieros;
        DatosVendedorResponse vendedor;
        String vendedorId;
        String vendedorNombre;
        String asesorAsignadoId;
        Timestamp fechaAsignacion;
        Double scoreDocumental;
        Double scoreGarantes;
        Double scoreEntrevista;
        Double scoreFinal;
        String decisionFinal;
        Double montoAprobado;
        String motivoDecision;
        Timestamp fechaDecisionFinal;
        List<String> condicionesAprobacion;
        String fortalezasCaso;
        String debilidadesCaso;
        String resultadoFinal;
        String evaluador;
        Boolean certificadoGenerado;
        String urlCertificado;
        Boolean contratoGenerado;
        String urlContrato;
        String observacionesGenerales;
        Timestamp createdAt;
        Timestamp updatedAt;
    }

    @Value @Builder
    public static class DatosFinancierosResponse {
        Double montoVehiculo;
        Double soatCostosNotariales;
        Double costoTotal;
        Double inicial;
        Double montoFinanciar;
        Integer numeroCuotasQuincenales;
        Double montoCuotaQuincenal;
        Double montoAbonarDealer;
        Double totalAPagar;
        Double porcentajeInicial;
    }

    @Value @Builder
    public static class DatosVendedorResponse {
        String id;
        String nombre;
        String tienda;
        String email;
        String telefono;
    }

    @Value @Builder
    public static class ClienteResponse {
        String id;
        String tipo;
        String nombres;
        String apellidoPaterno;
        String apellidoMaterno;
        String nombreCompleto;
        String sexo;
        String fechaNacimiento;
        String estadoCivil;
        Integer cargasFamiliares;
        String documentType;
        String documentNumber;
        String email;
        String telefono1;
        String telefono2;
        String departamento;
        String provincia;
        String distrito;
        String direccion;
        String ubicacionGPSCasa;
        String tipoVivienda;
        String licenciaConducir;
        String numeroLicencia;
        String ocupacion;
        Double ingresoMensual;
        String rangoIngresos;
        String perfilSentinel;
        Map<String, String> archivos;
        Map<String, EvaluacionDocumentoResponse> evaluacionDocumentos;
        String estadoValidacionDocumentos;
        List<String> documentosObservados;
        Boolean datosVerificados;
        String observacionesEvaluador;
        EvaluacionEntrevistaResponse evaluacionEntrevista;
        Timestamp createdAt;
        Timestamp updatedAt;
    }

    @Value @Builder
    public static class EvaluacionDocumentoResponse {
        String estado;
        String observaciones;
        Timestamp fechaEvaluacion;
        String evaluador;
    }

    @Value @Builder
    public static class EvaluacionEntrevistaResponse {
        String solicitudId;
        Timestamp fechaInicio;
        Timestamp fechaFin;
        String modalidad;
        String puntualidad;
        Integer presentacionPersonal;
        Integer actitudColaboracion;
        Integer coherenciaRespuestas;
        Integer nivelConfianza;
        Integer scoreEntrevista;
        String recomendacion;
        String motivoRecomendacion;
        Boolean esBorrador;
        Timestamp createdAt;
    }

    @Value @Builder
    public static class VehiculoResponse {
        String id;
        String marca;
        String modelo;
        String anio;
        String color;
        Double precioReferencial;
        Double cilindrada;
    }

    @Value @Builder
    public static class ReferenciaResponse {
        String id;
        Integer numero;
        String nombre;
        String apellidos;
        String telefono;
        String parentesco;
        String estadoVerificacion;
        String resultadoContacto;
        Integer scoreVerificacion;
        String observaciones;
        Timestamp fechaContacto;
        Boolean rechazada;
    }

    @Value @Builder
    public static class AsesorResponse {
        String id;
        String nombre;
        String email;
        String rol;
    }

    // ── Factory method ─────────────────────────────────────────────────────

    public static ExpedienteCompletoResponse from(com.motoyav2.evaluacion.domain.model.Expediente expediente) {
        return ExpedienteCompletoResponse.builder()
                .solicitud(toSolicitudResponse(expediente.getSolicitud()))
                .titular(toClienteResponse(expediente.getTitular()))
                .fiador(expediente.getFiador() != null ? toClienteResponse(expediente.getFiador()) : null)
                .vehiculo(toVehiculoResponse(expediente.getVehiculo()))
                .referencias(expediente.getReferencias() != null
                        ? expediente.getReferencias().stream().map(ExpedienteCompletoResponse::toReferenciaResponse).toList()
                        : List.of())
                .datosCompletos(expediente.datosCompletos())
                .asesorAsignado(expediente.getAsesorAsignado() != null
                        ? AsesorResponse.builder()
                                .id(expediente.getAsesorAsignado().getId())
                                .nombre(expediente.getAsesorAsignado().getNombre())
                                .email(expediente.getAsesorAsignado().getEmail())
                                .rol(expediente.getAsesorAsignado().getRol())
                                .build()
                        : null)
                .build();
    }

    private static SolicitudResponse toSolicitudResponse(Solicitud s) {
        if (s == null) return null;
        return SolicitudResponse.builder()
                .id(s.getId())
                .numeroSolicitud(s.getNumeroSolicitud())
                .estado(s.getEstado() != null ? s.getEstado().getFirestoreValue() : null)
                .prioridad(s.getPrioridad())
                .titularId(s.getTitularId())
                .fiadorId(s.getFiadorId())
                .vehiculoId(s.getVehiculoId())
                .referenciasIds(s.getReferenciasIds())
                .precioCompraMoto(s.getPrecioCompraMoto() != null ? s.getPrecioCompraMoto().doubleValue() : null)
                .inicial(s.getInicial() != null ? s.getInicial().doubleValue() : null)
                .montoCuota(s.getMontoCuota() != null ? s.getMontoCuota().doubleValue() : null)
                .plazoQuincenas(s.getPlazoQuincenas())
                .datosFinancieros(toDFResponse(s.getDatosFinancieros()))
                .vendedor(s.getVendedor() != null ? DatosVendedorResponse.builder()
                        .id(s.getVendedor().getId()).nombre(s.getVendedor().getNombre())
                        .tienda(s.getVendedor().getTienda()).email(s.getVendedor().getEmail())
                        .telefono(s.getVendedor().getTelefono()).build() : null)
                .vendedorNombre(s.getVendedorNombre())
                .asesorAsignadoId(s.getAsesorAsignadoId())
                .fechaAsignacion(s.getFechaAsignacion())
                .scoreDocumental(s.getScoreDocumental())
                .scoreGarantes(s.getScoreGarantes())
                .scoreEntrevista(s.getScoreEntrevista())
                .scoreFinal(s.getScoreFinal())
                .decisionFinal(s.getDecisionFinal() != null ? s.getDecisionFinal().getFirestoreValue() : null)
                .montoAprobado(s.getMontoAprobado() != null ? s.getMontoAprobado().doubleValue() : null)
                .motivoDecision(s.getMotivoDecision())
                .fechaDecisionFinal(s.getFechaDecisionFinal())
                .condicionesAprobacion(s.getCondicionesAprobacion())
                .fortalezasCaso(s.getFortalezasCaso())
                .debilidadesCaso(s.getDebilidadesCaso())
                .resultadoFinal(s.getResultadoFinal())
                .evaluador(s.getEvaluador())
                .certificadoGenerado(s.getCertificadoGenerado())
                .urlCertificado(s.getUrlCertificado())
                .contratoGenerado(s.getContratoGenerado())
                .urlContrato(s.getUrlContrato())
                .observacionesGenerales(s.getObservacionesGenerales())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private static DatosFinancierosResponse toDFResponse(com.motoyav2.evaluacion.domain.model.DatosFinancieros df) {
        if (df == null) return null;
        return DatosFinancierosResponse.builder()
                .montoVehiculo(df.getMontoVehiculo() != null ? df.getMontoVehiculo().doubleValue() : null)
                .soatCostosNotariales(df.getSoatCostosNotariales() != null ? df.getSoatCostosNotariales().doubleValue() : null)
                .costoTotal(df.getCostoTotal() != null ? df.getCostoTotal().doubleValue() : null)
                .inicial(df.getInicial() != null ? df.getInicial().doubleValue() : null)
                .montoFinanciar(df.getMontoFinanciar() != null ? df.getMontoFinanciar().doubleValue() : null)
                .numeroCuotasQuincenales(df.getNumeroCuotasQuincenales())
                .montoCuotaQuincenal(df.getMontoCuotaQuincenal() != null ? df.getMontoCuotaQuincenal().doubleValue() : null)
                .montoAbonarDealer(df.getMontoAbonarDealer() != null ? df.getMontoAbonarDealer().doubleValue() : null)
                .totalAPagar(df.getTotalAPagar() != null ? df.getTotalAPagar().doubleValue() : null)
                .porcentajeInicial(df.getPorcentajeInicial() != null ? df.getPorcentajeInicial().doubleValue() : null)
                .build();
    }

    private static ClienteResponse toClienteResponse(Cliente c) {
        if (c == null) return null;
        Map<String, EvaluacionDocumentoResponse> evalDocs = null;
        if (c.getEvaluacionDocumentos() != null) {
            evalDocs = new java.util.HashMap<>();
            for (Map.Entry<String, com.motoyav2.evaluacion.domain.model.EvaluacionDocumento> e : c.getEvaluacionDocumentos().entrySet()) {
                var ed = e.getValue();
                evalDocs.put(e.getKey(), EvaluacionDocumentoResponse.builder()
                        .estado(ed.getEstado()).observaciones(ed.getObservaciones())
                        .fechaEvaluacion(ed.getFechaEvaluacion()).evaluador(ed.getEvaluador()).build());
            }
        }
        EvaluacionEntrevistaResponse entRevista = null;
        if (c.getEvaluacionEntrevista() != null) {
            var ev = c.getEvaluacionEntrevista();
            entRevista = EvaluacionEntrevistaResponse.builder()
                    .solicitudId(ev.getSolicitudId()).fechaInicio(ev.getFechaInicio())
                    .fechaFin(ev.getFechaFin()).modalidad(ev.getModalidad())
                    .puntualidad(ev.getPuntualidad()).presentacionPersonal(ev.getPresentacionPersonal())
                    .actitudColaboracion(ev.getActitudColaboracion()).coherenciaRespuestas(ev.getCoherenciaRespuestas())
                    .nivelConfianza(ev.getNivelConfianza()).scoreEntrevista(ev.getScoreEntrevista())
                    .recomendacion(ev.getRecomendacion()).motivoRecomendacion(ev.getMotivoRecomendacion())
                    .esBorrador(ev.getEsBorrador()).createdAt(ev.getCreatedAt()).build();
        }
        return ClienteResponse.builder()
                .id(c.getId()).tipo(c.getTipo()).nombres(c.getNombres())
                .apellidoPaterno(c.getApellidoPaterno()).apellidoMaterno(c.getApellidoMaterno())
                .nombreCompleto(c.getNombreCompleto()).sexo(c.getSexo())
                .fechaNacimiento(c.getFechaNacimiento()).estadoCivil(c.getEstadoCivil())
                .cargasFamiliares(c.getCargasFamiliares()).documentType(c.getDocumentType())
                .documentNumber(c.getDocumentNumber()).email(c.getEmail())
                .telefono1(c.getTelefono1()).telefono2(c.getTelefono2())
                .departamento(c.getDepartamento()).provincia(c.getProvincia())
                .distrito(c.getDistrito()).direccion(c.getDireccion())
                .ubicacionGPSCasa(c.getUbicacionGPSCasa()).tipoVivienda(c.getTipoVivienda())
                .licenciaConducir(c.getLicenciaConducir()).numeroLicencia(c.getNumeroLicencia())
                .ocupacion(c.getOcupacion())
                .ingresoMensual(c.getIngresoMensual()).rangoIngresos(c.getRangoIngresos())
                .perfilSentinel(c.getPerfilSentinel()).archivos(c.getArchivos())
                .evaluacionDocumentos(evalDocs)
                .estadoValidacionDocumentos(c.getEstadoValidacionDocumentos())
                .documentosObservados(c.getDocumentosObservados())
                .datosVerificados(c.getDatosVerificados())
                .observacionesEvaluador(c.getObservacionesEvaluador())
                .evaluacionEntrevista(entRevista)
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .build();
    }

    private static VehiculoResponse toVehiculoResponse(Vehiculo v) {
        if (v == null) return null;
        return VehiculoResponse.builder()
                .id(v.getId()).marca(v.getMarca()).modelo(v.getModelo())
                .anio(v.getAnio()).color(v.getColor())
                .precioReferencial(v.getPrecioReferencial()).cilindrada(v.getCilindrada())
                .build();
    }

    private static ReferenciaResponse toReferenciaResponse(Referencia r) {
        if (r == null) return null;
        return ReferenciaResponse.builder()
                .id(r.getId()).numero(r.getNumero()).nombre(r.getNombre())
                .apellidos(r.getApellidos()).telefono(r.getTelefono())
                .parentesco(r.getParentesco()).estadoVerificacion(r.getEstadoVerificacion())
                .resultadoContacto(r.getResultadoContacto()).scoreVerificacion(r.getScoreVerificacion())
                .observaciones(r.getObservaciones()).fechaContacto(r.getFechaContacto())
                .rechazada(r.getRechazada())
                .build();
    }
}
