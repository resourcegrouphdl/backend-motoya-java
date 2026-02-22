package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpedienteDto {

    private String evaluacionId;
    private String numeroEvaluacion;
    private String codigoSolicitud;
    private String solicitudFirebaseId;
    private String estadoActual;
    private String etapaActual;
    private String prioridad;

    private ProgresoDto progreso;

    private PersonaDto titular;
    private PersonaDto fiador;
    private VehiculoDto vehiculo;
    private List<ReferenciaDto> referencias;
    private DatosFinancierosDto datosFinancieros;
    private EvaluacionEntrevistaDto evaluacionTitular;
    private EvaluacionEntrevistaDto evaluacionFiador;
    private List<EtapaDto> etapas;
    private ScoresDto scores;
    private List<AlertaDto> alertas;
    private List<DocumentoDto> documentos;
    private VendedorDto vendedor;
    private TiendaDto tienda;

    private String fechaCreacion;
    private String fechaUltimaActualizacion;
    private Long tiempoTranscurrido;
    private String asignadoA;
    private String nombreEvaluador;
    private String decisionFinal;
}
