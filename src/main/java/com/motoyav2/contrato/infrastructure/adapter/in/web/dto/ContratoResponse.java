package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ContratoResponse(
        String id,
        String numeroContrato,
        String estado,
        String fase,
        DatosTitularDto titular,
        DatosFiadorDto fiador,
        TiendaInfoDto tienda,
        DatosFinancierosDto datosFinancieros,
        BoucherPagoInicialDto boucherPagoInicial,
        FacturaVehiculoDto facturaVehiculo,
        List<CuotaCronogramaDto> cuotas,
        List<DocumentoGeneradoDto> documentosGenerados,
        List<EvidenciaFirmaDto> evidenciasFirma,
        List<NotificacionDto> notificaciones,
        String creadoPor,
        String evaluacionId,
        String motivoRechazo,
        Instant fechaCreacion,
        Instant fechaActualizacion
) {
    public record DatosTitularDto(
            String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
            String telefono, String email, String direccion, String distrito, String provincia, String departamento
    ) {}

    public record DatosFiadorDto(
            String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
            String telefono, String email, String direccion, String distrito, String provincia,
            String departamento, String parentesco
    ) {}

    public record TiendaInfoDto(
            String tiendaId, String nombreTienda, String direccion, String ciudad
    ) {}

    public record DatosFinancierosDto(
            BigDecimal precioVehiculo, BigDecimal cuotaInicial, BigDecimal montoFinanciado,
            BigDecimal tasaInteresAnual, int numeroCuotas, BigDecimal cuotaMensual
    ) {}

    public record BoucherPagoInicialDto(
            String id, String urlDocumento, String nombreArchivo, String tipoArchivo,
            Integer tamanioBytes, Instant fechaSubida, String estadoValidacion,
            String observacionesValidacion, String validadoPor, Instant fechaValidacion
    ) {}

    public record FacturaVehiculoDto(
            String id, String numeroFactura, String urlDocumento, String nombreArchivo,
            String tipoArchivo, Integer tamanioBytes, Instant fechaEmision, Instant fechaSubida,
            String marcaVehiculo, String modeloVehiculo, Integer anioVehiculo, String colorVehiculo,
            String serieMotor, String serieChasis, String estadoValidacion,
            String observacionesValidacion, String validadoPor, Instant fechaValidacion
    ) {}

    public record CuotaCronogramaDto(
            Integer numeroCuota, Instant fechaVencimiento, BigDecimal montoCuota,
            BigDecimal montoCapital, BigDecimal montoInteres, BigDecimal saldoPendiente,
            String estadoPago, Instant fechaPago, BigDecimal montoPagado,
            Integer diasMora, BigDecimal montoMora
    ) {}

    public record DocumentoGeneradoDto(
            String id, String tipo, String urlDocumento, String nombreArchivo,
            Instant fechaGeneracion, String generadoPor, Integer versionDocumento,
            String descargadoPor, Instant fechaDescarga
    ) {}

    public record EvidenciaFirmaDto(
            String id, String tipoEvidencia, String urlEvidencia, String nombreArchivo,
            String tipoArchivo, Integer tamanioBytes, Instant fechaSubida,
            String subidoPor, String descripcion
    ) {}

    public record NotificacionDto(
            String tipo, String mensaje, String destinatario, Instant fecha, Boolean exitoso
    ) {}
}
