package com.motoyav2.contrato.domain.service;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class GeneradorCodigoDeContrato {

 public String generarCodigoContrato() {
    String prefijo = "MTD-CR";

    String fecha = LocalDate.now()
        .format(DateTimeFormatter.BASIC_ISO_DATE);

    String uuidPart = UUID.randomUUID()
        .toString()
        .substring(0, 8)
        .toUpperCase();

    return prefijo + "-" + fecha + "-" + uuidPart;
  }
}
