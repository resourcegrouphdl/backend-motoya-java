package com.motoyav2.finanzas.application.port.in.command;

import lombok.Value;

@Value
public class ConfirmarPagoComisionCommand {
    String pagoId;
    String metodoPago;        // TRANSFERENCIA | YAPE | EFECTIVO
    String entidadBancaria;   // nullable
    String cuentaDestino;     // nullable
    String numeroOperacion;
    String voucherUrl;
    String voucherGcsPath;    // nullable
    String registradoPor;
}
