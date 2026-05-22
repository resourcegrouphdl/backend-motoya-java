package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

/**
 * Comprobante recibido por WhatsApp de un número no registrado en ningún caso activo.
 * El asesor lo asocia manualmente al caso correcto desde la pantalla "Sin identificar".
 * estado: PENDIENTE | ASOCIADO | DESCARTADO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "cobranzas-vouchers-sueltos")
public class VoucherSueltoDocument {

    @DocumentId
    private String id;

    /** Teléfono normalizado a 9 dígitos (sin +51). Clave de búsqueda. */
    private String telefono;

    /** Teléfono tal como llegó del webhook (+51XXXXXXXXX). */
    private String telefonoRaw;

    /** Ruta en GCS: cobranza-vouchers/{id}/filename.jpg */
    private String gcsPath;

    /** "image" | "document" */
    private String mediaType;

    /** PENDIENTE | ASOCIADO | DESCARTADO */
    private String estado;

    /** Relleno cuando el asesor asocia manualmente o hay auto-match por DNI. */
    private String contratoId;

    /** Nombre del titular del caso asociado. */
    private String clienteNombre;

    /** Datos que el remitente proporcionó vía WhatsApp (dni, textoRecibido). */
    private Map<String, String> datosProporcionados;

    /** Relleno cuando se genera el VoucherDocument tras la asociación. */
    private String voucherGeneradoId;

    /** Tienda a la que pertenece el caso (relleno al asociar). */
    private String storeId;

    private Date recibidoEn;
    private Date asociadoEn;
    private String asociadoPor;
    private String motivoDescarte;
}
