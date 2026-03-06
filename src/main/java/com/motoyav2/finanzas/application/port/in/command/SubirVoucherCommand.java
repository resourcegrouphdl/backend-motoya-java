package com.motoyav2.finanzas.application.port.in.command;

import lombok.Value;
import org.springframework.http.codec.multipart.FilePart;

@Value
public class SubirVoucherCommand {
    String facturaId;
    String pagoId;
    FilePart archivo;
}
