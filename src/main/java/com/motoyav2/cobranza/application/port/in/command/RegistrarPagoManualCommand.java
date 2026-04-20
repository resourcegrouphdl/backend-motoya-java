package com.motoyav2.cobranza.application.port.in.command;

public record RegistrarPagoManualCommand(
        String contratoId,
        /** Monto pagado */
        double monto,
        /**
         * Fecha real del pago en formato ISO YYYY-MM-DD.
         * Permite registros retroactivos para clientes migrados.
         */
        String fechaPago,
        /**
         * Si se conoce exactamente qué cuota se pagó (útil en migración).
         * Null para que el sistema aplique cronológicamente.
         */
        Integer numeroCuota,
        /** Texto libre para auditoría — ej: "Pago previo al sistema, verificado por XYZ" */
        String observaciones,
        /**
         * GCS path de la imagen del comprobante si se tiene.
         * Null si el pago no tiene comprobante físico.
         */
        String imagenPath,
        /**
         * Origen del pago manual:
         *   MIGRACION         — importación masiva de clientes anteriores al sistema
         *   ADMIN_MANUAL      — registro puntual por un admin / agente
         *   VOUCHER_FISICO    — se tiene comprobante escaneado pero sin flujo WhatsApp
         */
        String fuente,
        String registradoPor,
        String registradoPorNombre
) {}
