package com.motoyav2.contrato.domain.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ConvertirNumerosALetras {

  private static final String SIMBOLO_MONEDA = "";
  private static final String TIPO_MONEDA = "SOLES";

  public String convertir(BigDecimal valor) {

    if (valor == null) {
      return SIMBOLO_MONEDA + "CERO CON 00/100 " + TIPO_MONEDA;
    }

    BigDecimal monto = valor.setScale(2, RoundingMode.HALF_UP);

    long parteEntera = monto.longValue();
    int centavos = monto
        .remainder(BigDecimal.ONE)
        .movePointRight(2)
        .abs()
        .intValue();

    return SIMBOLO_MONEDA +
        convertirNumero(parteEntera).toUpperCase() +
        " CON " +
        String.format("%02d", centavos) +
        "/100 " +
        TIPO_MONEDA;
  }

  private String convertirNumero(long numero) {

    if (numero == 0) return "cero";
    if (numero < 0) return "menos " + convertirNumero(-numero);

    if (numero <= 15) {
      return switch ((int) numero) {
        case 1 -> "uno";
        case 2 -> "dos";
        case 3 -> "tres";
        case 4 -> "cuatro";
        case 5 -> "cinco";
        case 6 -> "seis";
        case 7 -> "siete";
        case 8 -> "ocho";
        case 9 -> "nueve";
        case 10 -> "diez";
        case 11 -> "once";
        case 12 -> "doce";
        case 13 -> "trece";
        case 14 -> "catorce";
        case 15 -> "quince";
        default -> "";
      };
    }

    if (numero < 20) return "dieci" + convertirNumero(numero - 10);
    if (numero == 20) return "veinte";
    if (numero < 30) return "veinti" + convertirNumero(numero - 20);

    if (numero < 100) {
      String[] decenas = {
          "", "", "veinte", "treinta", "cuarenta", "cincuenta",
          "sesenta", "setenta", "ochenta", "noventa"
      };
      return decenas[(int) numero / 10] +
          (numero % 10 > 0 ? " y " + convertirNumero(numero % 10) : "");
    }

    if (numero == 100) return "cien";
    if (numero < 200) return "ciento " + convertirNumero(numero - 100);

    if (numero < 1000) {
      String[] centenas = {
          "", "ciento", "doscientos", "trescientos", "cuatrocientos",
          "quinientos", "seiscientos", "setecientos",
          "ochocientos", "novecientos"
      };
      return centenas[(int) numero / 100] +
          (numero % 100 > 0 ? " " + convertirNumero(numero % 100) : "");
    }

    if (numero < 2000) {
      String resto = numero % 1000 > 0 ? " " + convertirNumero(numero % 1000) : "";
      return "mil" + resto;
    }

    if (numero < 1_000_000)
      return convertirNumero(numero / 1000) +
          " mil" +
          (numero % 1000 > 0 ? " " + convertirNumero(numero % 1000) : "");

    if (numero < 2_000_000)
      return "un millón" +
          (numero % 1_000_000 > 0 ? " " + convertirNumero(numero % 1_000_000) : "");

    if (numero < 1_000_000_000)
      return convertirNumero(numero / 1_000_000) +
          " millones" +
          (numero % 1_000_000 > 0 ? " " + convertirNumero(numero % 1_000_000) : "");

    return "";
  }

}
