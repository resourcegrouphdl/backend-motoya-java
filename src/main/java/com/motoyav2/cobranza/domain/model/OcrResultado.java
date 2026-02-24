package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OcrResultado {

    private String banco;
    private String numeroOperacion;
    /** ISO date del depósito */
    private String fecha;
    private Double monto;
    /** Score de confianza 0.0–1.0 */
    private Double confianza;
    /** ProcesadorOcr: DOCUMENT_AI | VISION_API | MANUAL */
    private String procesador;
}
