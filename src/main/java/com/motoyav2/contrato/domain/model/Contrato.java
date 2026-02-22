package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.FaseContrato;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record Contrato(
        String id,
        String numeroContrato,
        EstadoContrato estado,
        FaseContrato fase,
        DatosTitular titular,
        DatosFiador fiador,
        TiendaInfo tienda,
        DatosFinancieros datosFinancieros,
        BoucherPagoInicial boucherPagoInicial,
        FacturaVehiculo facturaVehiculo,
        List<CuotaCronograma> cuotas,
        List<DocumentoGenerado> documentosGenerados,
        List<EvidenciaFirma> evidenciasFirma,
        List<Notificacion> notificaciones,
        String creadoPor,
        String evaluacionId,
        String motivoRechazo,
        Instant fechaCreacion,
        Instant fechaActualizacion,
        ContratoParaImprimir contratoParaImprimir
) {


}
