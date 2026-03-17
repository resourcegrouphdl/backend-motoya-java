package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.model.Documentos;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseCliente;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClienteMapper {

  private ClienteMapper() {}

  @SuppressWarnings("unchecked")
  public static Persona toDomain(FirebaseCliente doc) {
    String tipoPersona = doc.getTipo() != null ? doc.getTipo().toUpperCase() : null;

    // Resolver campos con nombres alternativos (Firestore legacy vs nuevo)
    String tipoVivienda = doc.getTipoDeVivienda() != null ? doc.getTipoDeVivienda() : doc.getTipoVivienda();
    String licencia     = doc.getLicenciaDeConducir() != null ? doc.getLicenciaDeConducir() : doc.getLicenciaConducir();
    String numLicencia  = doc.getNumeroDeLicencia() != null ? doc.getNumeroDeLicencia() : doc.getNumeroLicencia();
    String empresa      = doc.getNombreEmpresa() != null ? doc.getNombreEmpresa() : doc.getNombreTrabajoEmpresa();

    // Parsear evaluacionDocumentos: Map<String, Object> → Map<String, Map<String, Object>>
    Map<String, Map<String, Object>> evalDocs = null;
    if (doc.getEvaluacionDocumentos() != null) {
      evalDocs = new HashMap<>();
      for (Map.Entry<String, Object> entry : doc.getEvaluacionDocumentos().entrySet()) {
        if (entry.getValue() instanceof Map<?, ?> m) {
          evalDocs.put(entry.getKey(), (Map<String, Object>) m);
        }
      }
    }

    return Persona.builder()
        .id(doc.getId())
        .tipo(doc.getTipo())
        .nombres(doc.getNombres())
        .apellidoPaterno(doc.getApellidoPaterno())
        .apellidoMaterno(doc.getApellidoMaterno())
        .nombreCompleto(buildNombreCompleto(doc))
        .tipoDeDocumento(doc.getDocumentType())
        .numeroDeDocumento(doc.getDocumentNumber())
        .nacionalidad(doc.getNacionalidad())
        .estadoResidenciaCE(doc.getEstadoResidenciaCE())
        .estadoCarnetPlastico(doc.getEstadoCarnetPlastico())
        .sexo(doc.getSexo())
        .email(doc.getEmail())
        .telefono1(doc.getTelefono1())
        .telefono2(doc.getTelefono2())
        .estadoCivil(doc.getEstadoCivil())
        .cargasFamiliares(doc.getCargasFamiliares())
        .cargasFamiliaresNum(parsearEntero(doc.getCargasFamiliares()))
        .fechaNacimiento(doc.getFechaNacimiento())
        .edad(doc.getEdad())
        .departamento(doc.getDepartamento())
        .provincia(doc.getProvincia())
        .distrito(doc.getDistrito())
        .direccion(doc.getDireccion())
        .direccionCompleta(buildDireccionCompleta(doc))
        .tipoDeVivienda(tipoVivienda)
        .antiguedadDomiciliaria(doc.getAntiguedadDomiciliaria())
        .referenciaUbicacion(doc.getReferenciaUbicacion())
        .ubicacionGpsLat(doc.getUbicacionGpsLat())
        .ubicacionGpsLng(doc.getUbicacionGpsLng())
        .ubicacionGPSCasa(doc.getUbicacionGPSCasa())
        .ocupacion(doc.getOcupacion())
        .tipoTrabajo(doc.getTipoTrabajo())
        .nombreEmpresa(empresa)
        .direccionDelTrabajo(doc.getDireccionDelTrabajo())
        .ubicacionDelTrabajoLat(doc.getUbicacionDelTrabajoLat())
        .ubicacionDelTrabajoLng(doc.getUbicacionDelTrabajoLng())
        .antiguedadDelTrabajo(doc.getAntiguedadDelTrabajo())
        .ingresoMensual(doc.getIngresoMensual())
        .ingresoMensualNum(parsearDouble(doc.getIngresoMensual()))
        .rangoIngresos(doc.getRangoIngresos())
        .frecuenciaIngresos(doc.getFrecuenciaIngresos())
        .licenciaDeConducir(licencia)
        .numeroDeLicencia(numLicencia)
        .vencimientoLicencia(doc.getVencimientoLicencia())
        .licenciaVigente(doc.getLicenciaVigente())
        .reflejaLicenciaWebMTC(doc.getReflejaLicenciaWebMTC())
        .tienePapeletasPendientes(doc.getTienePapeletasPendientes())
        .totalDeudaPapeletas(doc.getTotalDeudaPapeletas())
        .perfilSentinel(doc.getPerfilSentinel())
        .totalDeudaBancos(doc.getTotalDeudaBancos())
        .totalOtrasDeudas(doc.getTotalOtrasDeudas())
        .estadoValidacionDocumentos(doc.getEstadoValidacionDocumentos())
        .documentosObservados(doc.getDocumentosObservados())
        .datosVerificados(doc.getDatosVerificados())
        .observacionesEvaluador(doc.getObservacionesEvaluador())
        .evaluacionDocumentos(evalDocs)
        .evaluacionEntrevista(EntrevistaMapper.fromMap(doc.getEvaluacionEntrevista()))
        .documentos(mapArchivosToDocumentos(doc.getArchivos(), tipoPersona))
        .creadoEn(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
        .actualizadoEn(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
        .fechaValidacionDocumentos(doc.getFechaValidacionDocumentos() != null
            ? doc.getFechaValidacionDocumentos().toString() : null)
        .build();
  }

  private static String buildNombreCompleto(FirebaseCliente doc) {
    StringBuilder sb = new StringBuilder();
    if (doc.getNombres() != null) sb.append(doc.getNombres());
    if (doc.getApellidoPaterno() != null) sb.append(" ").append(doc.getApellidoPaterno());
    if (doc.getApellidoMaterno() != null) sb.append(" ").append(doc.getApellidoMaterno());
    return sb.toString().trim();
  }

  private static String buildDireccionCompleta(FirebaseCliente doc) {
    StringBuilder sb = new StringBuilder();
    if (doc.getDireccion() != null) sb.append(doc.getDireccion());
    if (doc.getDistrito() != null) sb.append(", ").append(doc.getDistrito());
    if (doc.getProvincia() != null) sb.append(", ").append(doc.getProvincia());
    return sb.isEmpty() ? null : sb.toString();
  }

  private static List<Documentos> mapArchivosToDocumentos(Map<String, String> archivos, String tipoPersona) {
    if (archivos == null || archivos.isEmpty()) return List.of();
    List<Documentos> lista = new ArrayList<>();
    archivos.forEach((tipo, url) -> lista.add(
        Documentos.builder()
            .tipoDocumento(tipo)
            .tipoPersona(tipoPersona)
            .url(url)
            .nombre(tipo)
            .build()
    ));
    return lista;
  }

  /** Parsea "1500" → 1500.0; null/blank → null */
  private static Double parsearDouble(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try { return Double.parseDouble(valor.replaceAll("[^0-9.]", "")); }
    catch (NumberFormatException e) { return null; }
  }

  /** Parsea "2" → 2; null/blank/non-numeric → null */
  private static Integer parsearEntero(String valor) {
    if (valor == null || valor.isBlank()) return null;
    try { return Integer.parseInt(valor.replaceAll("[^0-9]", "")); }
    catch (NumberFormatException e) { return null; }
  }
}
