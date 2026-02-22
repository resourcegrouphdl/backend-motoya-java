package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.model.Documentos;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseCliente;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClienteMapper {

  private ClienteMapper() {
  }

  public static Persona toDomain(FirebaseCliente doc) {
    String tipoPersona = doc.getTipo() != null ? doc.getTipo().toUpperCase() : null;

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
        .sexo(doc.getSexo())
        .email(doc.getEmail())
        .telefono1(doc.getTelefono1())
        .telefono2(doc.getTelefono2())
        .estadoCivil(doc.getEstadoCivil())
        .cargasFamiliares(doc.getCargasFamiliares())
        .fechaNacimiento(doc.getFechaNacimiento())
        .edad(doc.getEdad())
        .departamento(doc.getDepartamento())
        .provincia(doc.getProvincia())
        .distrito(doc.getDistrito())
        .direccion(doc.getDireccion())
        .direccionCompleta(buildDireccionCompleta(doc))
        .tipoDeVivienda(doc.getTipoDeVivienda())
        .antiguedadDomiciliaria(doc.getAntiguedadDomiciliaria())
        .referenciaUbicacion(doc.getReferenciaUbicacion())
        .ubicacionGpsLat(doc.getUbicacionGpsLat())
        .ubicacionGpsLng(doc.getUbicacionGpsLng())
        .ocupacion(doc.getOcupacion())
        .tipoTrabajo(doc.getTipoTrabajo())
        .nombreEmpresa(doc.getNombreEmpresa())
        .direccionDelTrabajo(doc.getDireccionDelTrabajo())
        .ubicacionDelTrabajoLat(doc.getUbicacionDelTrabajoLat())
        .ubicacionDelTrabajoLng(doc.getUbicacionDelTrabajoLng())
        .antiguedadDelTrabajo(doc.getAntiguedadDelTrabajo())
        .ingresoMensual(doc.getIngresoMensual())
        .rangoIngresos(doc.getRangoIngresos())
        .licenciaDeConducir(doc.getLicenciaDeConducir())
        .numeroDeLicencia(doc.getNumeroDeLicencia())
        .vencimientoLicencia(doc.getVencimientoLicencia())
        .licenciaVigente(doc.getLicenciaVigente())
        .documentos(mapArchivosToDocumentos(doc.getArchivos(), tipoPersona))
        .creadoEn(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
        .actualizadoEn(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
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

}
