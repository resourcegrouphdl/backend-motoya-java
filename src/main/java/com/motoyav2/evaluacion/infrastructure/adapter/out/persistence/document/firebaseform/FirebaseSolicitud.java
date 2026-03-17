package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Document(collectionName = "solicitudes")
public class FirebaseSolicitud {

  private String formularioId;
  private String numeroSolicitud;     // "MDCR-20251005-VS49J"
  private String codigoDeSolicitud;
  private String estado;              // EstadoSolicitud (30 valores)
  private String prioridad;           // 'Alta' | 'Media' | 'Baja'
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private String titularId;
  private String fiadorId;
  private String mensajeOpcional;
  private List<String> referenciasIds;
  private String vehiculoId;

  // financieros legacy (plazoQuincenas puede llegar como String o Long)
  private Object plazoQuincenas;      // ⚠️ string "16" o number 16
  private Long montoCuota;
  private Long precioCompraMoto;
  private Long inicial;

  // financieros nuevos (estructura nueva — opcional)
  private Map<String, Object> datosFinancieros;

  // vendedor / tienda
  private VendedorFirebase vendedor;
  private String vendedorId;
  private String vendedorNombre;
  private List<String> vendedorTienda;

  // asignación
  private String asesorAsignadoId;
  private Timestamp fechaAsignacion;

  // scores calculados
  private Double scoreDocumental;
  private Double scoreGarantes;
  private Double scoreEntrevista;
  private Double scoreFinal;

  // decisión final
  private String decisionFinal;
  private Double montoAprobado;
  private String motivoRechazo;
  private String motivoDecision;
  private Timestamp fechaDecisionFinal;
  private String usuarioDecision;
  private List<String> condicionesAprobacion;
  private String fortalezasCaso;
  private String debilidadesCaso;
  private String resultadoFinal;
  private String evaluador;

  // documentos generados
  private Boolean certificadoGenerado;
  private String urlCertificado;
  private String UrlDelCertificadoGenerado; // alias normalización
  private Timestamp fechaGeneracionCertificado;
  private Boolean contratoGenerado;
  private String urlContrato;
  private Timestamp fechaGeneracionContrato;

  private String observacionesGenerales;
}