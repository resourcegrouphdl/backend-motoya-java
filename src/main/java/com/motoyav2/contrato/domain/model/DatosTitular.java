package com.motoyav2.contrato.domain.model;

public record DatosTitular(
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        String telefono,
        String email,
        String direccion,
        String distrito,
        String provincia,
        String departamento
) {

  public String nombreCompleto() {
    String n = nombres == null ? "" : nombres.trim();
    String a = apellidos == null ? "" : apellidos.trim();
    return (n + " " + a).trim();
  }

}
