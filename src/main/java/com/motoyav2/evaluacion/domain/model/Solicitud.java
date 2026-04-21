package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.enums.Decision;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class Solicitud {
    String id;
    String numeroSolicitud;
    String codigoDeSolicitud;
    EstadoSolicitud estado;
    String prioridad;           // Alta | Media | Baja

    // Refs
    String titularId;
    String fiadorId;
    String vehiculoId;
    List<String> referenciasIds;

    // Financiero legacy
    BigDecimal precioCompraMoto;
    BigDecimal inicial;
    BigDecimal montoCuota;
    Integer plazoQuincenas;

    // Financiero nuevo
    DatosFinancieros datosFinancieros;

    // Titular desnormalizado (para listados sin lookup extra)
    String titularNombreCompleto;
    String titularDni;
    String titularTelefono;
    String titularEmail;

    // Fiador desnormalizado (para central de riesgo — permite query por fiadorDni)
    String fiadorDni;

    // Vendedor
    DatosVendedor vendedor;
    String vendedorId;
    String vendedorNombre;
    String mensajeOpcional;

    // Asesor
    String asesorAsignadoId;
    Timestamp fechaAsignacion;

    // Scores
    Double scoreDocumental;
    Double scoreGarantes;
    Double scoreEntrevista;
    Double scoreFinal;

    // Decisión
    Decision decisionFinal;
    BigDecimal montoAprobado;
    String motivoRechazo;
    String motivoDecision;
    Timestamp fechaDecisionFinal;
    String usuarioDecision;
    List<String> condicionesAprobacion;
    String fortalezasCaso;
    String debilidadesCaso;
    String resultadoFinal;
    String evaluador;

    // Documentos generados
    Boolean certificadoGenerado;
    String urlCertificado;
    Timestamp fechaGeneracionCertificado;
    Boolean contratoGenerado;
    String urlContrato;
    Timestamp fechaGeneracionContrato;

    String observacionesGenerales;

    // Indicadores automáticos
    String semaforoReferencias;   // verde | amarillo | rojo
    java.util.Map<String, Object> alertaDuplicado;

    // WhatsApp bienvenida
    Boolean titularBienvenidaEnviada;
    Boolean fiadorBienvenidaEnviada;

    Timestamp createdAt;
    Timestamp updatedAt;

    public boolean tieneAsesorAsignado() {
        return asesorAsignadoId != null && !asesorAsignadoId.isBlank();
    }

    public boolean tieneFiador() {
        return fiadorId != null && !fiadorId.isBlank();
    }
}
