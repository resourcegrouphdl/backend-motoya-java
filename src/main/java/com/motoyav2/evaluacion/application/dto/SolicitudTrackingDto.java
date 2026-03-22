package com.motoyav2.evaluacion.application.dto;

import com.motoyav2.evaluacion.domain.model.Solicitud;

import java.time.Instant;

public record SolicitudTrackingDto(
        String id,
        String codigoDeSolicitud,
        String numeroSolicitud,
        String estado,
        String estadoLabel,
        int progreso,
        String prioridad,
        String titularId,
        String fiadorId,
        String titularNombreCompleto,
        String titularDni,
        String titularTelefono,
        String titularEmail,
        String vehiculoDescripcion,
        Double precioCompraMoto,
        Double montoCuota,
        Integer plazoQuincenas,
        String motivoRechazo,
        /** Observaciones por documento del evaluador (ej: "dniFrente: foto borrosa; selfie: no coincide"). */
        String observacionesDocumentales,
        Boolean certificadoGenerado,
        String urlCertificado,
        Boolean contratoGenerado,
        Long createdAtEpoch,
        Long updatedAtEpoch
) {
    private static final java.util.Map<String, String> ESTADO_LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("pendiente", "Pendiente"),
            java.util.Map.entry("en_revision_inicial", "En Revisión Inicial"),
            java.util.Map.entry("evaluacion_documental", "Evaluación Documental"),
            java.util.Map.entry("documentos_observados", "Documentos Observados"),
            java.util.Map.entry("documentos_incompletos", "Documentos Incompletos"),
            java.util.Map.entry("documentos_completos", "Documentos Completos"),
            java.util.Map.entry("cliente_aprobado", "Cliente Aprobado"),
            java.util.Map.entry("cliente_rechazado", "Cliente Rechazado"),
            java.util.Map.entry("evaluacion_garantes", "Evaluación de Garantes"),
            java.util.Map.entry("fiador_aprobado", "Fiador Aprobado"),
            java.util.Map.entry("fiador_rechazado", "Fiador Rechazado"),
            java.util.Map.entry("referencias_aprobadas", "Referencias Aprobadas"),
            java.util.Map.entry("referencias_rechazadas", "Referencias Rechazadas"),
            java.util.Map.entry("vehiculo_aprobado", "Vehículo Aprobado"),
            java.util.Map.entry("vehiculo_rechazado", "Vehículo Rechazado"),
            java.util.Map.entry("datos_verificados", "Datos Verificados"),
            java.util.Map.entry("datos_no_verificados", "Datos No Verificados"),
            java.util.Map.entry("entrevista_programada", "Entrevista Programada"),
            java.util.Map.entry("entrevista_en_curso", "Entrevista en Curso"),
            java.util.Map.entry("entrevista_completada", "Entrevista Completada"),
            java.util.Map.entry("en_revision_final", "En Revisión Final"),
            java.util.Map.entry("aprobado", "Aprobado"),
            java.util.Map.entry("condicional", "Condicional"),
            java.util.Map.entry("rechazado", "Rechazado"),
            java.util.Map.entry("certificado_generado", "Certificado Generado"),
            java.util.Map.entry("esperando_inicial", "Esperando Pago Inicial"),
            java.util.Map.entry("contrato_generado", "Contrato Generado"),
            java.util.Map.entry("contrato_firmado", "Contrato Firmado"),
            java.util.Map.entry("entrega_completada", "Entrega Completada"),
            java.util.Map.entry("cancelado", "Cancelado")
    );

    private static final java.util.Map<String, Integer> ESTADO_PROGRESO = java.util.Map.ofEntries(
            java.util.Map.entry("pendiente", 5),
            java.util.Map.entry("en_revision_inicial", 10),
            java.util.Map.entry("evaluacion_documental", 20),
            java.util.Map.entry("documentos_observados", 20),
            java.util.Map.entry("documentos_incompletos", 20),
            java.util.Map.entry("documentos_completos", 30),
            java.util.Map.entry("cliente_aprobado", 35),
            java.util.Map.entry("cliente_rechazado", 0),
            java.util.Map.entry("evaluacion_garantes", 40),
            java.util.Map.entry("fiador_aprobado", 45),
            java.util.Map.entry("fiador_rechazado", 40),
            java.util.Map.entry("referencias_aprobadas", 55),
            java.util.Map.entry("referencias_rechazadas", 50),
            java.util.Map.entry("vehiculo_aprobado", 62),
            java.util.Map.entry("vehiculo_rechazado", 60),
            java.util.Map.entry("datos_verificados", 68),
            java.util.Map.entry("datos_no_verificados", 65),
            java.util.Map.entry("entrevista_programada", 72),
            java.util.Map.entry("entrevista_en_curso", 78),
            java.util.Map.entry("entrevista_completada", 82),
            java.util.Map.entry("en_revision_final", 88),
            java.util.Map.entry("aprobado", 92),
            java.util.Map.entry("condicional", 90),
            java.util.Map.entry("rechazado", 0),
            java.util.Map.entry("certificado_generado", 94),
            java.util.Map.entry("esperando_inicial", 96),
            java.util.Map.entry("contrato_generado", 97),
            java.util.Map.entry("contrato_firmado", 99),
            java.util.Map.entry("entrega_completada", 100),
            java.util.Map.entry("cancelado", 0)
    );

    public static SolicitudTrackingDto from(Solicitud s) {
        String estadoVal = s.getEstado() != null ? s.getEstado().getFirestoreValue() : "pendiente";
        Long epoch = s.getCreatedAt() != null ? s.getCreatedAt().toDate().getTime() : null;
        Long updatedEpoch = s.getUpdatedAt() != null ? s.getUpdatedAt().toDate().getTime() : epoch;

        Double precio = s.getDatosFinancieros() != null && s.getDatosFinancieros().getMontoVehiculo() != null
                ? s.getDatosFinancieros().getMontoVehiculo().doubleValue()
                : (s.getPrecioCompraMoto() != null ? s.getPrecioCompraMoto().doubleValue() : null);

        Double cuota = s.getDatosFinancieros() != null && s.getDatosFinancieros().getMontoCuotaQuincenal() != null
                ? s.getDatosFinancieros().getMontoCuotaQuincenal().doubleValue()
                : (s.getMontoCuota() != null ? s.getMontoCuota().doubleValue() : null);

        Integer plazo = s.getDatosFinancieros() != null && s.getDatosFinancieros().getNumeroCuotasQuincenales() != null
                ? s.getDatosFinancieros().getNumeroCuotasQuincenales()
                : s.getPlazoQuincenas();

        return new SolicitudTrackingDto(
                s.getId(),
                s.getCodigoDeSolicitud(),
                s.getNumeroSolicitud(),
                estadoVal,
                ESTADO_LABELS.getOrDefault(estadoVal, estadoVal),
                ESTADO_PROGRESO.getOrDefault(estadoVal, 0),
                s.getPrioridad(),
                s.getTitularId(),
                s.getFiadorId(),
                s.getTitularNombreCompleto(),
                s.getTitularDni(),
                s.getTitularTelefono(),
                s.getTitularEmail(),
                null, // vehiculoDescripcion se enriquece opcionalmente
                precio,
                cuota,
                plazo,
                s.getMotivoRechazo(),
                s.getObservacionesGenerales(),
                s.getCertificadoGenerado(),
                s.getUrlCertificado(),
                s.getContratoGenerado(),
                epoch,
                updatedEpoch
        );
    }
}
