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

  public  ContratoParaImprimir contratoParaImprimir(Contrato co) {

    String codigo = generadorCodigoDeContrato.generarCodigoContrato();

    return ContratoParaImprimir.builder()
        .codigo(codigo)
        .nombreTitular(co.titular().nombreCompleto())
        .tipoDeDocumento(co.titular().tipoDocumento().toUpperCase())
        .numeroDeDocumento(co.titular().numeroDocumento())
        .domicilioTitular(co.titular().direccion())
        .distritoTitular(co.titular().distrito())
        .nombreFiador(co.fiador().nombreCompleto())
        .tipoDocumentoFiador(co.fiador().tipoDocumento().toUpperCase())
        .numeroDocumentoFiador(co.fiador().numeroDocumento())
        .domicilioFiador(co.fiador().direccion())
        .distritoFiador(co.fiador().distrito())

        .marcaDeMoto(co.facturaVehiculo().marcaVehiculo())
        .modelo(co.facturaVehiculo().modeloVehiculo())
        .anioDelModelo(co.facturaVehiculo().anioVehiculo().toString())
        .placaDeRodaje("")
        .colorDeMoto(co.facturaVehiculo().colorVehiculo())
        .numeroDeSerie(co.facturaVehiculo().serieChasis())
        .numeroDeMotor(co.facturaVehiculo().serieMotor())

        .precioTotal(s2(co.datosFinancieros().precioVehiculo()))
        .precioTotalLetras(convertirNumerosALetra.convertir(co.datosFinancieros().precioVehiculo()))

        .inicial(s2(co.datosFinancieros().cuotaInicial()))
        .inicialLetras(convertirNumerosALetra.convertir(co.datosFinancieros().cuotaInicial()))

        .numeroDeQuincenas(co.datosFinancieros().numeroCuotas())
        .numeroDeMeses(co.datosFinancieros().numeroDeMeses())

        .montoDeLaQuincena(s2(co.datosFinancieros().cuotaMensual()))
        .montoDeLaQuincenaLetras(convertirNumerosALetra.convertir(co.datosFinancieros().cuotaMensual()))

        .proveedor(co.tienda().nombreTienda())

        .build();
  }

  private BigDecimal s2(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

}
