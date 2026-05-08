package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class ComprobanteContable {

    String id;
    String serie;
    String numero;
    String numeroCompleto;
    String tipo;
    String estado;
    String contratoId;
    String storeId;
    String receptorNombre;
    String receptorDocumento;
    Double subTotal;
    Double igv;
    Double total;
    LocalDate fechaEmision;
    LocalDate creadoEn;
}
