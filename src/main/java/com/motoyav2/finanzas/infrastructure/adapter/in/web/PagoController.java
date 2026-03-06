package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.RegistrarPagoUseCase;
import com.motoyav2.finanzas.application.port.in.SubirVoucherUseCase;
import com.motoyav2.finanzas.application.port.in.command.RegistrarPagoCommand;
import com.motoyav2.finanzas.application.port.in.command.SubirVoucherCommand;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.RegistrarPagoRequest;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
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

    @PostMapping(value = "/{pagoId}/voucher", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<FinanzasActionResponse> subirVoucher(
            @PathVariable String pagoId,
            @RequestParam String facturaId,
            @RequestPart("voucher") FilePart archivo) {
        SubirVoucherCommand command = new SubirVoucherCommand(facturaId, pagoId, archivo);
        return subirVoucher.ejecutar(command)
                .thenReturn(FinanzasActionResponse.ok("Comprobante adjuntado correctamente"));
    }
}
