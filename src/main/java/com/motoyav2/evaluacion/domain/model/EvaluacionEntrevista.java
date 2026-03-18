package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class EvaluacionEntrevista {
    String id;
    String solicitudId;
    Timestamp fechaInicio;
    Timestamp fechaFin;
    Integer duracionMinutos;
    String entrevistadorId;
    String entrevistadorNombre;
    String modalidad;       // videollamada | presencial | telefonica
    String plataforma;
    String puntualidad;     // puntual | retraso_leve | retraso_significativo | no_asistio
    String coordenadasCliente;
    String coordenadasFiador;
    Integer presentacionPersonal;
    Integer actitudColaboracion;
    Integer coherenciaRespuestas;
    Integer nivelConfianza;
    String observacionesCliente;
    String observacionesFiador;
    String observacionesDomicilio;
    String observacionesCapacidadPago;
    List<String> hallazgosPositivos;
    List<String> hallazgosNegativos;
    List<AlertaEntrevista> alertas;
    Integer scoreEntrevista;    // 0–100
    String recomendacion;       // aprobar | rechazar | condicional | requiere_comite | revisar
    String motivoRecomendacion;
    List<String> condiciones;
    String meetingUrl;
    String meetingId;
    Boolean meetingScheduled;
    Timestamp meetingScheduledDate;
    Boolean whatsappMessageSent;
    Timestamp whatsappMessageDate;
    Boolean esBorrador;
    Timestamp createdAt;
    Timestamp updatedAt;
}
