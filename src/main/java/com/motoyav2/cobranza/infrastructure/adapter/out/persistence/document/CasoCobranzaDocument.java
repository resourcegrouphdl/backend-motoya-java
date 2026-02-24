package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Aggregate Root del caso de cobranza.
 * ID del documento = contratoId (ej: CTR-1001).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "casos_cobranza")
public class CasoCobranzaDocument {

    @DocumentId
    private String contratoId;

    // Datos del cliente
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDni;
    /** Datos completos del titular embebidos (módulo cobranzas, aislado de contratos) */
    private DatosTitularDocument titular;
    private String motoDescripcion;

    // Organización
    private String storeId;
    private String agenteAsignadoId;
    private String agenteAsignadoNombre;

    // Estado del caso
    /** NivelEstrategia: MORA_TEMPRANA | MORA_MEDIA | MORA_CRITICA | JUDICIAL */
    private String nivelEstrategia;
    /** EstadoCaso: INTERVENCION_REQUERIDA | PROMESA_VIGENTE | EN_SEGUIMIENTO | PROMESA_VENCE_HOY | PROMESA_INCUMPLIDA */
    private String estadoCaso;
    /** CicloVidaCaso: ACTIVO | PROMESA_VIGENTE | ACUERDO_VIGENTE | PAGADO_TOTAL | JUDICIAL | CASTIGADO | CERRADO */
    private String cicloVida;

    // Saldos
    private Double saldoActual;
    private Double capitalOriginal;
    private Double totalPagado;
    private Double totalMora;
    private Double totalCondonado;

    // Cronograma
    private Date fechaVencimientoPrimerCuotaImpaga;
    private Integer numeroCuotasTotales;
    private Integer numeroCuotasPagadas;
    private List<CuotaCronogramaDocument> cronograma;

    // Gestión
    private Date ultimaGestion;
    /** Texto corto para lista. Ej: "Llamada - Sin respuesta" */
    private String ultimaGestionResumen;
    /** Ej: "WhatsApp automático en 2 días" */
    private String proximaAccion;

    // Excepciones
    private Boolean contactoBloqueado;
    /** ExcepcionCaso: FALLECIDO | INSOLVENTE | DISPUTA | OPT_OUT | JUDICIAL_ACTIVO */
    private String excepcionActiva;

    // Auditoría
    private Date creadoEn;
    private Date actualizadoEn;
    private String creadoPor;
}
