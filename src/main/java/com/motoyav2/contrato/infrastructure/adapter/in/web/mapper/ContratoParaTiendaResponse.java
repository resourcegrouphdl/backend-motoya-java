package com.motoyav2.contrato.infrastructure.adapter.in.web.mapper;

import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.*;

import java.util.List;

public final class ContratoParaTiendaResponse {

    private ContratoParaTiendaResponse() {}

    public static ContratoDetalleAPIDto toResponse(Contrato c) {
        return ContratoDetalleAPIDto.builder()
                .id(c.id())
                .numeroContrato(c.numeroContrato())
                .estado(c.estado() != null ? c.estado().name() : null)
                .fase(c.fase() != null ? c.fase().name() : null)
                .nombreTitular(c.titular() != null ? (c.titular().nombres() + " " + c.titular().apellidos()).trim() : null)
                .documentoTitular(c.titular() != null ? c.titular().numeroDocumento() : null)
                .tiendaNombre(c.tienda() != null ? c.tienda().nombreTienda() : null)
                .fechaCreacion(c.fechaCreacion() != null ? c.fechaCreacion().toString() : null)

                .marcaVehiculo(c.datosFinancieros() != null ? c.facturaVehiculo().marcaVehiculo() : null)
                .modeloVehiculo(c.datosFinancieros() != null ? c.facturaVehiculo().modeloVehiculo() : null)
                .anioVehiculo(c.datosFinancieros() != null ? c.facturaVehiculo().anioVehiculo().toString() : null)
                .colorVehiculo(c.datosFinancieros() != null ? c.facturaVehiculo().colorVehiculo() : null)
                .serieMotor(c.datosFinancieros() != null ? c.facturaVehiculo().serieMotor() : null)
                .serieChasis(c.datosFinancieros() != null ? c.facturaVehiculo().serieChasis() : null)

                .boucher(mapBoucher(c.boucherPagoInicial()))
                .factura(mapFactura(c.facturaVehiculo()))
                .evidenciaFirma(mapEvidencia(c.evidenciasFirma()))
                .documentosGenerados(mapDocumentosGenerados(c.documentosGenerados()))
                .numeroDeTitulo(mapNumeroDeTitulo(c))
                .tive(mapEvidenciaDocumento(c.tive()))
                .evidenciaSOAT(mapEvidenciaDocumento(c.evidenciaSOAT()))
                .evidenciaPlacaRodaje(mapEvidenciaDocumento(c.evidenciaPlacaRodaje()))
                .actaDeEntrega(mapEvidenciaDocumento(c.actaDeEntrega()))
                .build();
    }

    private static BoucherPagoInicialAPIDto mapBoucher(BoucherPagoInicial b) {
        if (b == null) return null;
        return BoucherPagoInicialAPIDto.builder()
                .id(b.id())
                .urlDocumento(b.urlDocumento())
                .nombreArchivo(b.nombreArchivo())
                .tipoArchivo(b.tipoArchivo())
                .tamanioBytes(b.tamanioBytes() != null ? b.tamanioBytes().toString() : null)
                .fechaSubida(b.fechaSubida() != null ? b.fechaSubida().toString() : null)
                .estadoValidacion(b.estadoValidacion())
                .validadoPor(b.validadoPor())
                .fechaValidacion(b.fechaValidacion() != null ? b.fechaValidacion().toString() : null)
                .observacionesValidacion(b.observacionesValidacion())
                .build();
    }

    private static FacturaVehiculoAPIDto mapFactura(FacturaVehiculo f) {
        if (f == null) return null;
        return FacturaVehiculoAPIDto.builder()
                .id(f.id())
                .numeroFactura(f.numeroFactura())
                .urlDocumento(f.urlDocumento())
                .nombreArchivo(f.nombreArchivo())
                .tipoArchivo(f.tipoArchivo())
                .tamanioBytes(f.tamanioBytes())
                .fechaEmision(f.fechaEmision() != null ? f.fechaEmision().toString() : null)
                .fechaSubida(f.fechaSubida() != null ? f.fechaSubida().toString() : null)
                .marcaVehiculo(f.marcaVehiculo())
                .modeloVehiculo(f.modeloVehiculo())
                .anioVehiculo(f.anioVehiculo())
                .colorVehiculo(f.colorVehiculo())
                .serieMotor(f.serieMotor())
                .serieChasis(f.serieChasis())
                .estadoValidacion(f.estadoValidacion())
                .validadoPor(f.validadoPor())
                .fechaValidacion(f.fechaValidacion() != null ? f.fechaValidacion().toString() : null)
                .observacionesValidacion(f.observacionesValidacion())
                .build();
    }

    private static EvidenciaFirmaAPIDto mapEvidencia(List<EvidenciaFirma> evidencias) {
        if (evidencias == null || evidencias.isEmpty()) return null;
        EvidenciaFirma e = evidencias.get(0);
        return new EvidenciaFirmaAPIDto(
                e.id(),
                e.tipoEvidencia() != null ? e.tipoEvidencia().name() : null,
                e.urlEvidencia(),
                e.nombreArchivo(),
                e.tipoArchivo(),
                e.tamanioBytes(),
                e.fechaSubida() != null ? e.fechaSubida().toString() : null,
                e.subidoPor(),
                e.descripcion(),
                e.estadoValidacion(),
                e.validadoPor(),
                e.fechaValidacion() != null ? e.fechaValidacion().toString() : null,
                e.observacionesValidacion()
        );
    }

    private static List<DocumentoGeneradoAPIDto> mapDocumentosGenerados(List<DocumentoGenerado> docs) {
        if (docs == null) return List.of();
        return docs.stream().map(d -> new DocumentoGeneradoAPIDto(
                d.tipo() != null ? d.tipo().name() : null,
                d.urlDocumento()
        )).toList();
    }

    private static NumeroDeTituloResponse mapNumeroDeTitulo(Contrato c) {
        if (c.numeroDeTitulo() == null) return null;
        return new NumeroDeTituloResponse(
                c.numeroDeTitulo(),
                c.fechaRegistroTitulo() != null ? c.fechaRegistroTitulo().toString() : null
        );
    }

    private static EvidenciaDocumentoResponse mapEvidenciaDocumento(EvidenciaDocumento ev) {
        if (ev == null) return null;
        return new EvidenciaDocumentoResponse(
                ev.id(),
                ev.tipoEvidencia(),
                ev.urlEvidencia(),
                ev.nombreArchivo(),
                ev.tipoArchivo(),
                ev.tamanioBytes(),
                ev.fechaSubida() != null ? ev.fechaSubida().toString() : null,
                ev.descripcion(),
                ev.estadoValidacion() != null ? ev.estadoValidacion().name() : null,
                ev.validadoPor(),
                ev.fechaValidacion() != null ? ev.fechaValidacion().toString() : null,
                ev.observacionesValidacion()
        );
    }
}
