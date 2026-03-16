package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.RegistrarPagoUseCase;
import com.motoyav2.finanzas.application.port.in.SubirVoucherUseCase;
import com.motoyav2.finanzas.application.port.in.command.RegistrarPagoCommand;
import com.motoyav2.finanzas.application.port.in.command.SubirVoucherCommand;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.RegistrarPagoRequest;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final RegistrarPagoUseCase registrarPago;
    private final SubirVoucherUseCase subirVoucher;

    @PostMapping
    public Mono<FinanzasActionResponse> registrar(@Valid @RequestBody RegistrarPagoRequest request) {
        RegistrarPagoCommand command = new RegistrarPagoCommand(
                request.getFacturaId(),
                request.getPagoId(),
                request.getMonto(),
                request.getFechaPago(),
                request.getMetodoPago()
        );
        return registrarPago.ejecutar(command)
                .thenReturn(FinanzasActionResponse.ok("Pago registrado correctamente"));
    }

    /**
     * Asocia un voucher ya subido a GCS a un pago de factura.
     *
     * El frontend debe haber obtenido primero la URL firmada via:
     *   POST /api/finanzas/storage/signed-upload-url
     * y subido el archivo directamente a GCS.
     *
     * Body:
     *   {
     *     "facturaId":  "FAC-20240101-ABCD",
     *     "voucherUrl": "https://storage.googleapis.com/motoya-form.appspot.com/finanzas/vouchers/...",
     *     "gcsPath":    "finanzas/vouchers/FAC-001/P1_1710000000.jpg",  (opcional — para Document AI)
     *     "mimeType":   "image/jpeg"                                    (opcional)
     *   }
     */
    @PostMapping("/{pagoId}/voucher")
    public Mono<FinanzasActionResponse> asociarVoucher(
            @PathVariable String pagoId,
            @Valid @RequestBody AsociarVoucherRequest body) {

        SubirVoucherCommand command = new SubirVoucherCommand(
                body.facturaId(),
                pagoId,
                body.voucherUrl(),
                body.gcsPath(),
                body.mimeType()
        );
        return subirVoucher.ejecutar(command)
                .thenReturn(FinanzasActionResponse.ok("Comprobante asociado correctamente"));
    }

    // ── DTO inline ────────────────────────────────────────────────────────────

    public record AsociarVoucherRequest(
            @NotBlank String facturaId,
            @NotBlank String voucherUrl,
            String gcsPath,
            String mimeType
    ) {}
}
