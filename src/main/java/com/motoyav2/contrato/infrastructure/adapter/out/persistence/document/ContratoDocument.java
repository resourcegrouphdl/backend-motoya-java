package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.Data;

import java.util.List;

@Data
@Document(collectionName = "contratos")
public class ContratoDocument {
    @DocumentId
    private String id;
    private String numeroContrato;
    private String estado;
    private String fase;
    private DatosTitularEmbedded titular;
    private DatosFiadorEmbedded fiador;
    private TiendaInfoEmbedded tienda;
    private DatosFinancierosEmbedded datosFinancieros;
    private List<BoucherPagoInicialEmbedded> boucheresPagoInicial;
    private FacturaVehiculoEmbedded facturaVehiculo;
    private List<CuotaCronogramaEmbedded> cuotas;
    private List<DocumentoGeneradoEmbedded> documentosGenerados;
    private List<EvidenciaFirmaEmbedded> evidenciasFirma;
    private List<NotificacionEmbedded> notificaciones;
    private String creadoPor;
    private String evaluacionId;
    private String motivoRechazo;
    private Timestamp fechaCreacion;
    private Timestamp fechaActualizacion;
    private ContratoParaImprimirEmbedded contratoParaImprimir;
    // Campos post-firma
    private String numeroDeTitulo;
    private Timestamp fechaRegistroTitulo;
    private EvidenciaDocumentoEmbedded tive;
    private EvidenciaDocumentoEmbedded evidenciaSOAT;
    private EvidenciaDocumentoEmbedded evidenciaPlacaRodaje;
    private EvidenciaDocumentoEmbedded actaDeEntrega;
}
