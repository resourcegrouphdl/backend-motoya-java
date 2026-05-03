package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosFiadorDocument;
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
@Document(collectionName = "cobranzas-casos")
public class CasoCobranzaDocument {

    @DocumentId
    private String contratoId;

    // Datos del cliente
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDni;
    /** Datos completos del titular embebidos (módulo cobranzas, aislado de contratos) */
    private DatosTitularDocument titular;
    /** Datos completos del fiador/garante embebidos para estrategias de cobro */
    private DatosFiadorDocument fiador;
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
    // Object acepta tanto Timestamp (docs nuevos) como String "YYYY-MM-DD" (docs migrados)
    private Object fechaVencimientoPrimerCuotaImpaga;
    private Integer numeroCuotasTotales;
    private Integer numeroCuotasPagadas;
    private List<CuotaCronogramaDocument> cronograma;

    /** Referencias personales del cliente para localización. */
    private List<java.util.Map<String, Object>> referencias;

    /** Observaciones internas del operador sobre este caso. */
    private String observaciones;

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

    // Mora automática
    /** Fecha del último recordatorio WA de mora enviado por el scheduler. */
    private Date ultimoRecordatorioMora;

    // Contactabilidad
    /** Mensajes INBOUND no leídos por el agente (se resetea al abrir el chat). */
    private Integer mensajesNoLeidos;
    /** Última vez que el cliente respondió un mensaje de WhatsApp. */
    private Date ultimaRespuestaCliente;

    // Auditoría
    private Date creadoEn;
    private Date actualizadoEn;
    private String creadoPor;
    private String actualizadoPor;
}
