package com.motoyav2.contrato.application.applicatioMapper;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoParaImprimir;
import com.motoyav2.contrato.domain.service.ConvertirNumerosALetras;
import com.motoyav2.contrato.domain.service.GeneradorCodigoDeContrato;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class ContratoParaDescargasMaper {

  private final GeneradorCodigoDeContrato generadorCodigoDeContrato;
  private final ConvertirNumerosALetras convertirNumerosALetra;

  public  ContratoParaImprimir contratoParaImprimir(Contrato co, String rucProveedor) {

    String codigo = generadorCodigoDeContrato.generarCodigoContrato();

    String tipoDocTitular = co.titular().tipoDocumento() != null
        ? co.titular().tipoDocumento().toUpperCase() : "";
    String nombreFiador = co.fiador() != null ? co.fiador().nombreCompleto() : "";
    String tipoDocFiador = (co.fiador() != null && co.fiador().tipoDocumento() != null)
        ? co.fiador().tipoDocumento().toUpperCase() : "";
    String numDocFiador = co.fiador() != null ? co.fiador().numeroDocumento() : "";
    String domFiador = co.fiador() != null ? co.fiador().direccion() : "";
    String distFiador = co.fiador() != null ? co.fiador().distrito() : "";
    String anioMoto = (co.facturaVehiculo() != null && co.facturaVehiculo().anioVehiculo() != null)
        ? co.facturaVehiculo().anioVehiculo().toString() : "";

    return ContratoParaImprimir.builder()
        .codigo(codigo)
        .nombreTitular(co.titular().nombreCompleto())
        .tipoDeDocumento(tipoDocTitular)
        .numeroDeDocumento(co.titular().numeroDocumento())
        .domicilioTitular(co.titular().direccion())
        .distritoTitular(co.titular().distrito())
        .nombreFiador(nombreFiador)
        .tipoDocumentoFiador(tipoDocFiador)
        .numeroDocumentoFiador(numDocFiador)
        .domicilioFiador(domFiador)
        .distritoFiador(distFiador)

        .marcaDeMoto(co.facturaVehiculo() != null ? co.facturaVehiculo().marcaVehiculo() : "")
        .modelo(co.facturaVehiculo() != null ? co.facturaVehiculo().modeloVehiculo() : "")
        .anioDelModelo(anioMoto)
        .placaDeRodaje("")
        .colorDeMoto(co.facturaVehiculo() != null ? co.facturaVehiculo().colorVehiculo() : "")
        .numeroDeSerie(co.facturaVehiculo() != null ? co.facturaVehiculo().serieChasis() : "")
        .numeroDeMotor(co.facturaVehiculo() != null ? co.facturaVehiculo().serieMotor() : "")

        .precioTotal(s2(co.datosFinancieros().precioVehiculo()))
        .precioTotalLetras(convertirNumerosALetra.convertir(co.datosFinancieros().precioVehiculo()))

        .inicial(s2(co.datosFinancieros().cuotaInicial()))
        .inicialLetras(convertirNumerosALetra.convertir(co.datosFinancieros().cuotaInicial()))

        .numeroDeQuincenas(co.datosFinancieros().numeroCuotas())
        .numeroDeMeses(co.datosFinancieros().numeroDeMeses())

        .montoDeLaQuincena(s2(co.datosFinancieros().cuotaMensual()))
        .montoDeLaQuincenaLetras(convertirNumerosALetra.convertir(co.datosFinancieros().cuotaMensual()))

        .proveedor(co.tienda().nombreTienda())
        .rucProveedor(rucProveedor)

        .build();
  }

  private BigDecimal s2(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

}
