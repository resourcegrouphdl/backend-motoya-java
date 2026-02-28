package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import lombok.Builder;

import java.util.List;
@Builder
public record ContratoDetalleAPIDto (
    String id ,
    String numeroContrato,
    String estado,
    String fase,
    String nombreTitular,
    String documentoTitular,
    String tiendaNombre,
    String fechaCreacion,
    String marcaVehiculo,
    String modeloVehiculo,
    String anioVehiculo,
    String colorVehiculo,
    String serieMotor,
    String serieChasis,

    // Documentos subidos
    List<BoucherPagoInicialAPIDto> bouchers,
    FacturaVehiculoAPIDto factura,
    EvidenciaFirmaAPIDto evidenciaFirma,

    // Documentos generados por el sistema
    List<DocumentoGeneradoAPIDto> documentosGenerados,

    // Campos post-firma
    NumeroDeTituloResponse numeroDeTitulo,
    EvidenciaDocumentoResponse tive,
    EvidenciaDocumentoResponse evidenciaSOAT,
    EvidenciaDocumentoResponse evidenciaPlacaRodaje,
    EvidenciaDocumentoResponse actaDeEntrega
) {
}
