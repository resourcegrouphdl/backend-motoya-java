package com.motoyav2.contrato.infrastructure.adapter.in.web.mapper;

import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.ContratoListItemDto;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.ContratoResponse;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.ContratoResponse.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ContratoResponseMapper {

    private ContratoResponseMapper() {
    }

    public static ContratoResponse toResponse(Contrato c) {
        return new ContratoResponse(
                c.id(), c.numeroContrato(),
                c.estado() != null ? c.estado().name() : null,
                c.fase() != null ? c.fase().name() : null,
                mapTitular(c.titular()),
                mapFiador(c.fiador()),
                mapTienda(c.tienda()),
                mapFinancieros(c.datosFinancieros()),
                mapBoucher(c.boucherPagoInicial()),
                mapFactura(c.facturaVehiculo()),
                mapCuotas(c.cuotas()),
                mapDocGenerados(c.documentosGenerados()),
                mapEvidencias(c.evidenciasFirma()),
                mapNotificaciones(c.notificaciones()),
                c.creadoPor(), c.evaluacionId(), c.motivoRechazo(),
                c.fechaCreacion(), c.fechaActualizacion()
        );
    }

    public static ContratoListItemDto toListItemDto(ContratoListItem item) {
        return new ContratoListItemDto(
                item.id(), item.numeroContrato(),
                item.estado() != null ? item.estado().name() : null,
                item.fase() != null ? item.fase().name() : null,
                item.nombreTitular(), item.documentoTitular(),
                item.tiendaNombre(), item.fechaCreacion()
        );
    }

    private static DatosTitularDto mapTitular(DatosTitular t) {
        if (t == null) return null;
        return new DatosTitularDto(t.nombres(), t.apellidos(), t.tipoDocumento(), t.numeroDocumento(),
                t.telefono(), t.email(), t.direccion(), t.distrito(), t.provincia(), t.departamento());
    }

    private static DatosFiadorDto mapFiador(DatosFiador f) {
        if (f == null) return null;
        return new DatosFiadorDto(f.nombres(), f.apellidos(), f.tipoDocumento(), f.numeroDocumento(),
                f.telefono(), f.email(), f.direccion(), f.distrito(), f.provincia(),
                f.departamento(), f.parentesco());
    }

    private static TiendaInfoDto mapTienda(TiendaInfo t) {
        if (t == null) return null;
        return new TiendaInfoDto(t.tiendaId(), t.nombreTienda(), t.direccion(), t.ciudad());
    }

    private static DatosFinancierosDto mapFinancieros(DatosFinancieros f) {
        if (f == null) return null;
        return new DatosFinancierosDto(
                scale2(f.precioVehiculo()), scale2(f.cuotaInicial()), scale2(f.montoFinanciado()),
                f.tasaInteresAnual(), f.numeroCuotas(), scale2(f.cuotaMensual()));
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BoucherPagoInicialDto mapBoucher(BoucherPagoInicial b) {
        if (b == null) return null;
        return new BoucherPagoInicialDto(
                b.id(), b.urlDocumento(), b.nombreArchivo(), b.tipoArchivo(),
                b.tamanioBytes(), b.fechaSubida(),
                b.estadoValidacion() != null ? b.estadoValidacion().name() : null,
                b.observacionesValidacion(), b.validadoPor(), b.fechaValidacion()
        );
    }

    private static FacturaVehiculoDto mapFactura(FacturaVehiculo f) {
        if (f == null) return null;
        return new FacturaVehiculoDto(
                f.id(), f.numeroFactura(), f.urlDocumento(), f.nombreArchivo(),
                f.tipoArchivo(), f.tamanioBytes(), f.fechaEmision(), f.fechaSubida(),
                f.marcaVehiculo(), f.modeloVehiculo(), f.anioVehiculo(), f.colorVehiculo(),
                f.serieMotor(), f.serieChasis(),
                f.estadoValidacion() != null ? f.estadoValidacion().name() : null,
                f.observacionesValidacion(), f.validadoPor(), f.fechaValidacion()
        );
    }

    private static List<CuotaCronogramaDto> mapCuotas(List<CuotaCronograma> list) {
        if (list == null) return List.of();
        return list.stream().map(c -> new CuotaCronogramaDto(
                c.numeroCuota(), c.fechaVencimiento(), scale2(c.montoCuota()),
                scale2(c.montoCapital()), scale2(c.montoInteres()), scale2(c.saldoPendiente()),
                c.estadoPago() != null ? c.estadoPago().name() : null,
                c.fechaPago(), scale2(c.montoPagado()), c.diasMora(), scale2(c.montoMora())
        )).toList();
    }

    private static List<DocumentoGeneradoDto> mapDocGenerados(List<DocumentoGenerado> list) {
        if (list == null) return List.of();
        return list.stream().map(d -> new DocumentoGeneradoDto(
                d.id(), d.tipo() != null ? d.tipo().name() : null,
                d.urlDocumento(), d.nombreArchivo(), d.fechaGeneracion(),
                d.generadoPor(), d.versionDocumento(), d.descargadoPor(), d.fechaDescarga()
        )).toList();
    }

    private static List<EvidenciaFirmaDto> mapEvidencias(List<EvidenciaFirma> list) {
        if (list == null) return List.of();
        return list.stream().map(e -> new EvidenciaFirmaDto(
                e.id(), e.tipoEvidencia() != null ? e.tipoEvidencia().name() : null,
                e.urlEvidencia(), e.nombreArchivo(), e.tipoArchivo(),
                e.tamanioBytes(), e.fechaSubida(), e.subidoPor(), e.descripcion()
        )).toList();
    }

    private static List<NotificacionDto> mapNotificaciones(List<Notificacion> list) {
        if (list == null) return List.of();
        return list.stream().map(n -> new NotificacionDto(
                n.tipo(), n.mensaje(), n.destinatario(), n.fecha(), n.exitoso()
        )).toList();
    }
}
