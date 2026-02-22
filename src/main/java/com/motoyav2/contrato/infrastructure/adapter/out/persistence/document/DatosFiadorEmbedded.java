package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import lombok.Data;

@Data
public class DatosFiadorEmbedded {
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefono;
    private String email;
    private String direccion;
    private String distrito;
    private String provincia;
    private String departamento;
    private String parentesco;
}
