package com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FinanzasActionResponse {
    private String status;
    private String message;

    public static FinanzasActionResponse ok(String mensaje) {
        return new FinanzasActionResponse("OK", mensaje);
    }
}
