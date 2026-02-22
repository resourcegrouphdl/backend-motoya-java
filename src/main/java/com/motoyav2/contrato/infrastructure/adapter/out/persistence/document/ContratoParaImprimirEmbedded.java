package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import lombok.Data;

@Data
public class ContratoParaImprimirEmbedded {
    private String codigo;
    private String nombreTitular;
    private String tipoDeDocumento;
    private String numeroDeDocumento;
    private String domicilioTitular;
    private String distritoTitular;
    private String nombreFiador;
    private String tipoDocumentoFiador;
    private String numeroDocumentoFiador;
    private String domicilioFiador;
    private String distritoFiador;
    private String marcaDeMoto;
    private String modelo;
    private String anioDelModelo;
    private String placaDeRodaje;
    private String colorDeMoto;
    private String numeroDeSerie;
    private String numeroDeMotor;
    private Double precioTotal;
    private String precioTotalLetras;
    private Double inicial;
    private String inicialLetras;
    private Integer numeroDeQuincenas;
    private Integer numeroDeMeses;
    private Double montoDeLaQuincena;
    private String montoDeLaQuincenaLetras;
    private String proveedor;
}
