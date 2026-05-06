package com.motoyav2.migracion.domain.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Registro de staging para migración desde Google Calendar.
 * Colección: migracion-staging (prefijo migracion- para diferenciarlo del resto).
 * Se puede eliminar completa una vez terminada la migración.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "migracion-staging")
public class MigracionStagingDocument {

    @DocumentId
    private String id;

    /** INCOMPLETO | COMPLETO | MIGRADO | ERROR */
    private String estado;
    /** 0–100: (camposCompletos / 5) * 100 */
    private Integer completitud;

    // ── Datos extraídos de Google Calendar ───────────────────────────────────

    private String clienteNombreCalendar;
    private Integer totalCuotas;
    private Double montoCuota;
    private Double capitalInferido;
    /** YYYY-MM-DD — fecha del primer evento del cliente */
    private String fechaInicio;
    /** Números de cuota marcadas como pagadas (por colorId) */
    private List<Integer> cuotasPagadas;
    private List<CuotaStagingDocument> cronogramaCalendar;

    // ── Datos completados manualmente ────────────────────────────────────────

    private String contratoId;
    private String clienteNombre;
    private String titularTipoDocumento;
    private String clienteDni;
    private String telefono;
    private String email;
    private String moto;

    // Dirección del titular y tienda
    private String storeId;
    private String direccion;
    private String distrito;
    private String provincia;
    private String departamento;

    // ── Referencias personales del cliente ───────────────────────────────────
    private List<ReferenciaDocument> referencias;

    // ── Observaciones internas ────────────────────────────────────────────────
    private String observaciones;

    // ── Datos del fiador (para migración asistida) ────────────────────────────
    private String fiadorNombre;
    private String fiadorApellidos;
    private String fiadorTipoDocumento;
    private String fiadorDni;
    private String fiadorTelefono;
    private String fiadorEmail;
    private String fiadorParentesco;

    // ── Resultado de la migración ─────────────────────────────────────────────

    private String contratoIdCreado;
    private String errorDetalle;
    private Date migradoEn;

    // ── Auditoría ─────────────────────────────────────────────────────────────

    private Date creadoEn;
    private String creadoPor;
    private Date actualizadoEn;
    private String actualizadoPor;
    private String migradoPor;
}
