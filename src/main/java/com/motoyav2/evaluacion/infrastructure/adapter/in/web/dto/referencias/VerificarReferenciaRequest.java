package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Request para registrar el resultado de la verificación de una referencia.
 *
 * estadoVerificacion: 'verificado' | 'rechazado' | 'no_contactado'
 * resultadoContacto:  'positivo - ok' | 'no contesta' | 'negativo'
 * scoreVerificacion:  0-100 (evaluación cualitativa del verificador)
 * calificacion:       'excelente' | 'bueno' | 'regular' | 'malo'
 * actitudDuranteContacto: descripción libre
 * rechazada:          true si la referencia se descarta definitivamente
 * respuestasPreguntas: mapa libre de preguntas → respuestas del cuestionario
 */
@Getter
@NoArgsConstructor
public class VerificarReferenciaRequest {

    private String estadoVerificacion;          // verificado | rechazado | no_contactado
    private String resultadoContacto;
    private Double scoreVerificacion;           // 0-100
    private Double scoreMaximo;                 // default 100
    private String calificacion;
    private String actitudDuranteContacto;
    private String observaciones;
    private Boolean rechazada;
    private Map<String, Object> respuestasPreguntas;
    private String verificadorId;
    private String verificadorNombre;
}
