package com.motoyav2.contrato.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContratoParaImprimir {

  private String codigo;
  private String nombreTitular;
  private String tipoDeDocumento;
  private String numeroDeDocumento;
  private String domicilioTitular;
  private String distritoTitular;
  //
  private String nombreFiador;
  private String tipoDocumentoFiador;
  private String numeroDocumentoFiador;
  private String domicilioFiador;
  private String distritoFiador;
  //
  private String marcaDeMoto;
  private String modelo;
  private String anioDelModelo;
  private String placaDeRodaje;
  private String colorDeMoto;
  private String numeroDeSerie;
  private String numeroDeMotor;

  private BigDecimal precioTotal;
  private String precioTotalLetras;

  private BigDecimal inicial;
  private String inicialLetras;

  private Integer numeroDeQuincenas;
  private Integer numeroDeMeses;
  private BigDecimal montoDeLaQuincena;
  private String montoDeLaQuincenaLetras;

  private String proveedor;
  private String rucProveedor;

}
