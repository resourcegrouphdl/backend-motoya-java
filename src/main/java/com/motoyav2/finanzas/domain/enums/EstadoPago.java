package com.motoyav2.finanzas.domain.enums;

public enum EstadoPago {
    PAGADO,
    PENDIENTE,
    EN_PROCESO,      // incluida en un batch de pago, pendiente de confirmación
    PROXIMO_VENCER,
    VENCIDO
}
