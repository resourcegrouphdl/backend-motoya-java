package com.motoyav2.contrato.infrastructure.adapter.out.persistence.mapper;

import com.google.cloud.Timestamp;
import com.motoyav2.contrato.domain.enums.*;
import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ContratoDocumentMapper {

    private ContratoDocumentMapper() {
    }

    public static Contrato toDomain(ContratoDocument doc) {
        return new Contrato(
                doc.getId(),
                doc.getNumeroContrato(),
                doc.getEstado() != null ? EstadoContrato.valueOf(doc.getEstado()) : null,
                doc.getFase() != null ? FaseContrato.valueOf(doc.getFase()) : null,
                mapTitularToDomain(doc.getTitular()),
                mapFiadorToDomain(doc.getFiador()),
                mapTiendaToDomain(doc.getTienda()),
                mapFinancierosToDomain(doc.getDatosFinancieros()),
                mapBoucherToDomain(doc.getBoucherPagoInicial()),
                mapFacturaToDomain(doc.getFacturaVehiculo()),
                mapCuotasToDomain(doc.getCuotas()),
                mapDocGeneradosToDomain(doc.getDocumentosGenerados()),
                mapEvidenciasToDomain(doc.getEvidenciasFirma()),
                mapNotificacionesToDomain(doc.getNotificaciones()),
                doc.getCreadoPor(),
                doc.getEvaluacionId(),
                doc.getMotivoRechazo(),
                toInstant(doc.getFechaCreacion()),
                toInstant(doc.getFechaActualizacion()),
                mapContratoParaImprimirToDomain(doc.getContratoParaImprimir())
        );
    }

    public static ContratoDocument toDocument(Contrato contrato) {
        ContratoDocument doc = new ContratoDocument();
        doc.setId(contrato.id());
        doc.setNumeroContrato(contrato.numeroContrato());
        doc.setEstado(contrato.estado() != null ? contrato.estado().name() : null);
        doc.setFase(contrato.fase() != null ? contrato.fase().name() : null);
        doc.setTitular(mapTitularToDoc(contrato.titular()));
        doc.setFiador(mapFiadorToDoc(contrato.fiador()));
        doc.setTienda(mapTiendaToDoc(contrato.tienda()));
        doc.setDatosFinancieros(mapFinancierosToDoc(contrato.datosFinancieros()));
        doc.setBoucherPagoInicial(mapBoucherToDoc(contrato.boucherPagoInicial()));
        doc.setFacturaVehiculo(mapFacturaToDoc(contrato.facturaVehiculo()));
        doc.setCuotas(mapCuotasToDoc(contrato.cuotas()));
        doc.setDocumentosGenerados(mapDocGeneradosToDoc(contrato.documentosGenerados()));
        doc.setEvidenciasFirma(mapEvidenciasToDoc(contrato.evidenciasFirma()));
        doc.setNotificaciones(mapNotificacionesToDoc(contrato.notificaciones()));
        doc.setCreadoPor(contrato.creadoPor());
        doc.setEvaluacionId(contrato.evaluacionId());
        doc.setMotivoRechazo(contrato.motivoRechazo());
        doc.setFechaCreacion(toTimestamp(contrato.fechaCreacion()));
        doc.setFechaActualizacion(toTimestamp(contrato.fechaActualizacion()));
        doc.setContratoParaImprimir(mapContratoParaImprimirToDoc(contrato.contratoParaImprimir()));
        return doc;
    }

    public static ContratoListItem toListItem(ContratoDocument doc) {
        String nombreTitular = "";
        String documentoTitular = "";
        String tiendaNombre = "";

        if (doc.getTitular() != null) {
            nombreTitular = (doc.getTitular().getNombres() + " " + doc.getTitular().getApellidos()).trim();
            documentoTitular = doc.getTitular().getNumeroDocumento();
        }
        if (doc.getTienda() != null) {
            tiendaNombre = doc.getTienda().getNombreTienda();
        }

        return new ContratoListItem(
                doc.getId(),
                doc.getNumeroContrato(),
                doc.getEstado() != null ? EstadoContrato.valueOf(doc.getEstado()) : null,
                doc.getFase() != null ? FaseContrato.valueOf(doc.getFase()) : null,
                nombreTitular,
                documentoTitular,
                tiendaNombre,
                toInstant(doc.getFechaCreacion())
        );
    }

    // --- Titular ---
    private static DatosTitular mapTitularToDomain(DatosTitularEmbedded e) {
        if (e == null) return null;
        return new DatosTitular(e.getNombres(), e.getApellidos(), e.getTipoDocumento(), e.getNumeroDocumento(),
                e.getTelefono(), e.getEmail(), e.getDireccion(), e.getDistrito(), e.getProvincia(), e.getDepartamento());
    }

    private static DatosTitularEmbedded mapTitularToDoc(DatosTitular t) {
        if (t == null) return null;
        DatosTitularEmbedded e = new DatosTitularEmbedded();
        e.setNombres(t.nombres());
        e.setApellidos(t.apellidos());
        e.setTipoDocumento(t.tipoDocumento());
        e.setNumeroDocumento(t.numeroDocumento());
        e.setTelefono(t.telefono());
        e.setEmail(t.email());
        e.setDireccion(t.direccion());
        e.setDistrito(t.distrito());
        e.setProvincia(t.provincia());
        e.setDepartamento(t.departamento());
        return e;
    }

    // --- Fiador ---
    private static DatosFiador mapFiadorToDomain(DatosFiadorEmbedded e) {
        if (e == null) return null;
        return new DatosFiador(e.getNombres(), e.getApellidos(), e.getTipoDocumento(), e.getNumeroDocumento(),
                e.getTelefono(), e.getEmail(), e.getDireccion(), e.getDistrito(), e.getProvincia(),
                e.getDepartamento(), e.getParentesco());
    }

    private static DatosFiadorEmbedded mapFiadorToDoc(DatosFiador f) {
        if (f == null) return null;
        DatosFiadorEmbedded e = new DatosFiadorEmbedded();
        e.setNombres(f.nombres());
        e.setApellidos(f.apellidos());
        e.setTipoDocumento(f.tipoDocumento());
        e.setNumeroDocumento(f.numeroDocumento());
        e.setTelefono(f.telefono());
        e.setEmail(f.email());
        e.setDireccion(f.direccion());
        e.setDistrito(f.distrito());
        e.setProvincia(f.provincia());
        e.setDepartamento(f.departamento());
        e.setParentesco(f.parentesco());
        return e;
    }

    // --- Tienda ---
    private static TiendaInfo mapTiendaToDomain(TiendaInfoEmbedded e) {
        if (e == null) return null;
        return new TiendaInfo(e.getTiendaId(), e.getNombreTienda(), e.getDireccion(), e.getCiudad());
    }

    private static TiendaInfoEmbedded mapTiendaToDoc(TiendaInfo t) {
        if (t == null) return null;
        TiendaInfoEmbedded e = new TiendaInfoEmbedded();
        e.setTiendaId(t.tiendaId());
        e.setNombreTienda(t.nombreTienda());
        e.setDireccion(t.direccion());
        e.setCiudad(t.ciudad());
        return e;
    }

    // --- Datos Financieros ---
    private static DatosFinancieros mapFinancierosToDomain(DatosFinancierosEmbedded e) {
        if (e == null) return null;
        return new DatosFinancieros(
                toBigDecimal(e.getPrecioVehiculo()), toBigDecimal(e.getCuotaInicial()),
                toBigDecimal(e.getMontoFinanciado()), toBigDecimal(e.getTasaInteresAnual()),
                e.getNumeroCuotas() != null ? e.getNumeroCuotas() : 0,
                toBigDecimal(e.getCuotaMensual())
        );
    }

    private static DatosFinancierosEmbedded mapFinancierosToDoc(DatosFinancieros f) {
        if (f == null) return null;
        DatosFinancierosEmbedded e = new DatosFinancierosEmbedded();
        e.setPrecioVehiculo(toDouble(f.precioVehiculo()));
        e.setCuotaInicial(toDouble(f.cuotaInicial()));
        e.setMontoFinanciado(toDouble(f.montoFinanciado()));
        e.setTasaInteresAnual(toDouble(f.tasaInteresAnual()));
        e.setNumeroCuotas(f.numeroCuotas());
        e.setCuotaMensual(toDouble(f.cuotaMensual()));
        return e;
    }

    // --- BoucherPagoInicial ---
    private static BoucherPagoInicial mapBoucherToDomain(BoucherPagoInicialEmbedded e) {
        if (e == null) return null;
        return BoucherPagoInicial.builder()
                .id(e.getId())
                .urlDocumento(e.getUrlDocumento())
                .nombreArchivo(e.getNombreArchivo())
                .tipoArchivo(e.getTipoArchivo())
                .tamanioBytes(e.getTamanioBytes())
                .fechaSubida(toInstant(e.getFechaSubida()))
                .estadoValidacion(e.getEstadoValidacion() != null ? EstadoValidacion.valueOf(e.getEstadoValidacion()) : null)
                .observacionesValidacion(e.getObservacionesValidacion())
                .validadoPor(e.getValidadoPor())
                .fechaValidacion(toInstant(e.getFechaValidacion()))
                .build();
    }

    private static BoucherPagoInicialEmbedded mapBoucherToDoc(BoucherPagoInicial b) {
        if (b == null) return null;
        BoucherPagoInicialEmbedded e = new BoucherPagoInicialEmbedded();
        e.setId(b.id());
        e.setUrlDocumento(b.urlDocumento());
        e.setNombreArchivo(b.nombreArchivo());
        e.setTipoArchivo(b.tipoArchivo());
        e.setTamanioBytes(b.tamanioBytes());
        e.setFechaSubida(toTimestamp(b.fechaSubida()));
        e.setEstadoValidacion(b.estadoValidacion() != null ? b.estadoValidacion().name() : null);
        e.setObservacionesValidacion(b.observacionesValidacion());
        e.setValidadoPor(b.validadoPor());
        e.setFechaValidacion(toTimestamp(b.fechaValidacion()));
        return e;
    }

    // --- FacturaVehiculo ---
    private static FacturaVehiculo mapFacturaToDomain(FacturaVehiculoEmbedded e) {
        if (e == null) return null;
        return FacturaVehiculo.builder()
                .id(e.getId())
                .numeroFactura(e.getNumeroFactura())
                .urlDocumento(e.getUrlDocumento())
                .nombreArchivo(e.getNombreArchivo())
                .tipoArchivo(e.getTipoArchivo())
                .tamanioBytes(e.getTamanioBytes())
                .fechaEmision(toInstant(e.getFechaEmision()))
                .fechaSubida(toInstant(e.getFechaSubida()))
                .marcaVehiculo(e.getMarcaVehiculo())
                .modeloVehiculo(e.getModeloVehiculo())
                .anioVehiculo(e.getAnioVehiculo())
                .colorVehiculo(e.getColorVehiculo())
                .serieMotor(e.getSerieMotor())
                .serieChasis(e.getSerieChasis())
                .estadoValidacion(e.getEstadoValidacion() != null ? EstadoValidacion.valueOf(e.getEstadoValidacion()) : null)
                .observacionesValidacion(e.getObservacionesValidacion())
                .validadoPor(e.getValidadoPor())
                .fechaValidacion(toInstant(e.getFechaValidacion()))
                .build();
    }

    private static FacturaVehiculoEmbedded mapFacturaToDoc(FacturaVehiculo f) {
        if (f == null) return null;
        FacturaVehiculoEmbedded e = new FacturaVehiculoEmbedded();
        e.setId(f.id());
        e.setNumeroFactura(f.numeroFactura());
        e.setUrlDocumento(f.urlDocumento());
        e.setNombreArchivo(f.nombreArchivo());
        e.setTipoArchivo(f.tipoArchivo());
        e.setTamanioBytes(f.tamanioBytes());
        e.setFechaEmision(toTimestamp(f.fechaEmision()));
        e.setFechaSubida(toTimestamp(f.fechaSubida()));
        e.setMarcaVehiculo(f.marcaVehiculo());
        e.setModeloVehiculo(f.modeloVehiculo());
        e.setAnioVehiculo(f.anioVehiculo());
        e.setColorVehiculo(f.colorVehiculo());
        e.setSerieMotor(f.serieMotor());
        e.setSerieChasis(f.serieChasis());
        e.setEstadoValidacion(f.estadoValidacion() != null ? f.estadoValidacion().name() : null);
        e.setObservacionesValidacion(f.observacionesValidacion());
        e.setValidadoPor(f.validadoPor());
        e.setFechaValidacion(toTimestamp(f.fechaValidacion()));
        return e;
    }

    // --- Cuotas ---
    private static List<CuotaCronograma> mapCuotasToDomain(List<CuotaCronogramaEmbedded> list) {
        if (list == null) return List.of();
        return list.stream().map(e -> CuotaCronograma.builder()
                .numeroCuota(e.getNumeroCuota() != null ? e.getNumeroCuota() : 0)
                .fechaVencimiento(toInstant(e.getFechaVencimiento()))
                .montoCuota(toBigDecimal(e.getMontoCuota()))
                .montoCapital(toBigDecimal(e.getMontoCapital()))
                .montoInteres(toBigDecimal(e.getMontoInteres()))
                .saldoPendiente(toBigDecimal(e.getSaldoPendiente()))
                .estadoPago(e.getEstadoPago() != null ? EstadoDePago.valueOf(e.getEstadoPago()) : null)
                .fechaPago(toInstant(e.getFechaPago()))
                .montoPagado(toBigDecimal(e.getMontoPagado()))
                .diasMora(e.getDiasMora())
                .montoMora(toBigDecimal(e.getMontoMora()))
                .build()
        ).toList();
    }

    private static List<CuotaCronogramaEmbedded> mapCuotasToDoc(List<CuotaCronograma> list) {
        if (list == null) return List.of();
        return list.stream().map(c -> {
            CuotaCronogramaEmbedded e = new CuotaCronogramaEmbedded();
            e.setNumeroCuota(c.numeroCuota());
            e.setFechaVencimiento(toTimestamp(c.fechaVencimiento()));
            e.setMontoCuota(toDouble(c.montoCuota()));
            e.setMontoCapital(toDouble(c.montoCapital()));
            e.setMontoInteres(toDouble(c.montoInteres()));
            e.setSaldoPendiente(toDouble(c.saldoPendiente()));
            e.setEstadoPago(c.estadoPago() != null ? c.estadoPago().name() : null);
            e.setFechaPago(toTimestamp(c.fechaPago()));
            e.setMontoPagado(toDouble(c.montoPagado()));
            e.setDiasMora(c.diasMora());
            e.setMontoMora(toDouble(c.montoMora()));
            return e;
        }).toList();
    }

    // --- Documentos Generados ---
    private static List<DocumentoGenerado> mapDocGeneradosToDomain(List<DocumentoGeneradoEmbedded> list) {
        if (list == null) return List.of();
        return list.stream().map(e -> DocumentoGenerado.builder()
                .id(e.getId())
                .tipo(e.getTipo() != null ? TipoDocumentoGenerado.valueOf(e.getTipo()) : null)
                .urlDocumento(e.getUrlDocumento())
                .nombreArchivo(e.getNombreArchivo())
                .fechaGeneracion(toInstant(e.getFechaGeneracion()))
                .generadoPor(e.getGeneradoPor())
                .versionDocumento(e.getVersionDocumento())
                .descargadoPor(e.getDescargadoPor())
                .fechaDescarga(toInstant(e.getFechaDescarga()))
                .build()
        ).toList();
    }

    private static List<DocumentoGeneradoEmbedded> mapDocGeneradosToDoc(List<DocumentoGenerado> list) {
        if (list == null) return List.of();
        return list.stream().map(d -> {
            DocumentoGeneradoEmbedded e = new DocumentoGeneradoEmbedded();
            e.setId(d.id());
            e.setTipo(d.tipo() != null ? d.tipo().name() : null);
            e.setUrlDocumento(d.urlDocumento());
            e.setNombreArchivo(d.nombreArchivo());
            e.setFechaGeneracion(toTimestamp(d.fechaGeneracion()));
            e.setGeneradoPor(d.generadoPor());
            e.setVersionDocumento(d.versionDocumento());
            e.setDescargadoPor(d.descargadoPor());
            e.setFechaDescarga(toTimestamp(d.fechaDescarga()));
            return e;
        }).toList();
    }

    // --- Evidencias Firma ---
    private static List<EvidenciaFirma> mapEvidenciasToDomain(List<EvidenciaFirmaEmbedded> list) {
        if (list == null) return List.of();
        return list.stream().map(e -> EvidenciaFirma.builder()
                .id(e.getId())
                .tipoEvidencia(e.getTipoEvidencia() != null ? TipoEvidencia.valueOf(e.getTipoEvidencia()) : null)
                .urlEvidencia(e.getUrlEvidencia())
                .nombreArchivo(e.getNombreArchivo())
                .tipoArchivo(e.getTipoArchivo())
                .tamanioBytes(e.getTamanioBytes())
                .fechaSubida(toInstant(e.getFechaSubida()))
                .subidoPor(e.getSubidoPor())
                .descripcion(e.getDescripcion())
                .build()
        ).toList();
    }

    private static List<EvidenciaFirmaEmbedded> mapEvidenciasToDoc(List<EvidenciaFirma> list) {
        if (list == null) return List.of();
        return list.stream().map(ev -> {
            EvidenciaFirmaEmbedded e = new EvidenciaFirmaEmbedded();
            e.setId(ev.id());
            e.setTipoEvidencia(ev.tipoEvidencia() != null ? ev.tipoEvidencia().name() : null);
            e.setUrlEvidencia(ev.urlEvidencia());
            e.setNombreArchivo(ev.nombreArchivo());
            e.setTipoArchivo(ev.tipoArchivo());
            e.setTamanioBytes(ev.tamanioBytes());
            e.setFechaSubida(toTimestamp(ev.fechaSubida()));
            e.setSubidoPor(ev.subidoPor());
            e.setDescripcion(ev.descripcion());
            return e;
        }).toList();
    }

    // --- Notificaciones ---
    private static List<Notificacion> mapNotificacionesToDomain(List<NotificacionEmbedded> list) {
        if (list == null) return List.of();
        return list.stream().map(e -> new Notificacion(
                e.getTipo(), e.getMensaje(), e.getDestinatario(), toInstant(e.getFecha()), e.getExitoso()
        )).toList();
    }

    private static List<NotificacionEmbedded> mapNotificacionesToDoc(List<Notificacion> list) {
        if (list == null) return List.of();
        return list.stream().map(n -> {
            NotificacionEmbedded e = new NotificacionEmbedded();
            e.setTipo(n.tipo());
            e.setMensaje(n.mensaje());
            e.setDestinatario(n.destinatario());
            e.setFecha(toTimestamp(n.fecha()));
            e.setExitoso(n.exitoso());
            return e;
        }).toList();
    }

    // --- ContratoParaImprimir ---
    private static ContratoParaImprimir mapContratoParaImprimirToDomain(ContratoParaImprimirEmbedded e) {
        if (e == null) return ContratoParaImprimir.builder().build();
        return ContratoParaImprimir.builder()
                .codigo(e.getCodigo())
                .nombreTitular(e.getNombreTitular())
                .tipoDeDocumento(e.getTipoDeDocumento())
                .numeroDeDocumento(e.getNumeroDeDocumento())
                .domicilioTitular(e.getDomicilioTitular())
                .distritoTitular(e.getDistritoTitular())
                .nombreFiador(e.getNombreFiador())
                .tipoDocumentoFiador(e.getTipoDocumentoFiador())
                .numeroDocumentoFiador(e.getNumeroDocumentoFiador())
                .domicilioFiador(e.getDomicilioFiador())
                .distritoFiador(e.getDistritoFiador())
                .marcaDeMoto(e.getMarcaDeMoto())
                .modelo(e.getModelo())
                .anioDelModelo(e.getAnioDelModelo())
                .placaDeRodaje(e.getPlacaDeRodaje())
                .colorDeMoto(e.getColorDeMoto())
                .numeroDeSerie(e.getNumeroDeSerie())
                .numeroDeMotor(e.getNumeroDeMotor())
                .precioTotal(toBigDecimal(e.getPrecioTotal()))
                .precioTotalLetras(e.getPrecioTotalLetras())
                .inicial(toBigDecimal(e.getInicial()))
                .inicialLetras(e.getInicialLetras())
                .numeroDeQuincenas(e.getNumeroDeQuincenas())
                .numeroDeMeses(e.getNumeroDeMeses())
                .montoDeLaQuincena(toBigDecimal(e.getMontoDeLaQuincena()))
                .montoDeLaQuincenaLetras(e.getMontoDeLaQuincenaLetras())
                .proveedor(e.getProveedor())
                .build();
    }

    private static ContratoParaImprimirEmbedded mapContratoParaImprimirToDoc(ContratoParaImprimir cp) {
        if (cp == null) return null;
        ContratoParaImprimirEmbedded e = new ContratoParaImprimirEmbedded();
        e.setCodigo(cp.getCodigo());
        e.setNombreTitular(cp.getNombreTitular());
        e.setTipoDeDocumento(cp.getTipoDeDocumento());
        e.setNumeroDeDocumento(cp.getNumeroDeDocumento());
        e.setDomicilioTitular(cp.getDomicilioTitular());
        e.setDistritoTitular(cp.getDistritoTitular());
        e.setNombreFiador(cp.getNombreFiador());
        e.setTipoDocumentoFiador(cp.getTipoDocumentoFiador());
        e.setNumeroDocumentoFiador(cp.getNumeroDocumentoFiador());
        e.setDomicilioFiador(cp.getDomicilioFiador());
        e.setDistritoFiador(cp.getDistritoFiador());
        e.setMarcaDeMoto(cp.getMarcaDeMoto());
        e.setModelo(cp.getModelo());
        e.setAnioDelModelo(cp.getAnioDelModelo());
        e.setPlacaDeRodaje(cp.getPlacaDeRodaje());
        e.setColorDeMoto(cp.getColorDeMoto());
        e.setNumeroDeSerie(cp.getNumeroDeSerie());
        e.setNumeroDeMotor(cp.getNumeroDeMotor());
        e.setPrecioTotal(toDouble(cp.getPrecioTotal()));
        e.setPrecioTotalLetras(cp.getPrecioTotalLetras());
        e.setInicial(toDouble(cp.getInicial()));
        e.setInicialLetras(cp.getInicialLetras());
        e.setNumeroDeQuincenas(cp.getNumeroDeQuincenas());
        e.setNumeroDeMeses(cp.getNumeroDeMeses());
        e.setMontoDeLaQuincena(toDouble(cp.getMontoDeLaQuincena()));
        e.setMontoDeLaQuincenaLetras(cp.getMontoDeLaQuincenaLetras());
        e.setProveedor(cp.getProveedor());
        return e;
    }

    // --- Utility ---
    private static Instant toInstant(Timestamp ts) {
        if (ts == null) return null;
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }

    private static Timestamp toTimestamp(Instant instant) {
        if (instant == null) return null;
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private static BigDecimal toBigDecimal(Double val) {
        return val != null ? BigDecimal.valueOf(val) : BigDecimal.ZERO;
    }

    private static Double toDouble(BigDecimal val) {
        return val != null ? val.doubleValue() : null;
    }
}
