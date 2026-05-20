package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.EnviarComprobanteWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.EnviarMensajeWhatsappUseCase;
import com.motoyav2.cobranza.application.port.in.command.EnviarComprobanteWhatsappCommand;
import com.motoyav2.cobranza.application.port.in.command.EnviarMensajeWhatsappCommand;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.ComprobantePagoPort;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnviarComprobanteWhatsappService implements EnviarComprobanteWhatsappUseCase {

    private final VoucherPort voucherPort;
    private final CasoCobranzaPort casoPort;
    private final ComprobantePagoPort comprobantePagoPort;
    private final WhatsAppMediaStorageService storageService;
    private final EnviarMensajeWhatsappUseCase enviarMensajeWhatsappUseCase;

    @Override
    public Mono<String> ejecutar(EnviarComprobanteWhatsappCommand command) {
        return voucherPort.findById(command.voucherId())
                .switchIfEmpty(Mono.error(new NotFoundException("Voucher no encontrado: " + command.voucherId())))
                .flatMap(voucher -> {
                    if (voucher.getContratoId() == null) {
                        return Mono.error(new BadRequestException(
                                "El voucher no tiene contrato vinculado. Vincúlelo antes de enviar."));
                    }
                    return casoPort.findById(voucher.getContratoId())
                            .switchIfEmpty(Mono.error(new NotFoundException(
                                    "Caso cobranza no encontrado: " + voucher.getContratoId())))
                            .flatMap(caso -> {
                                String telefono = caso.getClienteTelefono();
                                if (telefono == null || telefono.isBlank()) {
                                    return Mono.error(new BadRequestException(
                                            "El cliente no tiene teléfono WhatsApp registrado."));
                                }
                                String storeId = command.storeId() != null
                                        ? command.storeId() : caso.getStoreId();

                                return resolverGcsPath(command, voucher)
                                        .flatMap(gcsPath -> storageService.generarSignedUrl(gcsPath, 60 * 24 * 7))
                                        .flatMap(signedUrl -> {
                                            String mensaje = construirMensaje(caso.getClienteNombre(), signedUrl);
                                            EnviarMensajeWhatsappCommand waCmd = new EnviarMensajeWhatsappCommand(
                                                    voucher.getContratoId(), null, null,
                                                    command.agenteId(), command.agenteNombre(),
                                                    storeId, telefono, mensaje);
                                            log.info("[EnviarComprobanteWA] voucherId={} contratoId={} tel={}",
                                                    command.voucherId(), voucher.getContratoId(), telefono);
                                            return enviarMensajeWhatsappUseCase.ejecutar(waCmd);
                                        });
                            });
                });
    }

    /** Resuelve el GCS path del documento a enviar según prioridad definida en el contrato de la interfaz. */
    private Mono<String> resolverGcsPath(EnviarComprobanteWhatsappCommand command, VoucherDocument voucher) {
        if (command.archivoGcsPath() != null && !command.archivoGcsPath().isBlank()) {
            log.debug("[EnviarComprobanteWA] Usando archivo subido manualmente: {}", command.archivoGcsPath());
            return Mono.just(command.archivoGcsPath());
        }
        if (voucher.getComprobanteId() != null && !voucher.getComprobanteId().isBlank()) {
            return comprobantePagoPort.findById(voucher.getComprobanteId())
                    .flatMap(comp -> {
                        if (comp.getPdfPath() != null && !comp.getPdfPath().isBlank()) {
                            log.debug("[EnviarComprobanteWA] Usando PDF del comprobante: {}", comp.getPdfPath());
                            return Mono.just(comp.getPdfPath());
                        }
                        return fallbackAImagenVoucher(voucher);
                    })
                    .switchIfEmpty(fallbackAImagenVoucher(voucher));
        }
        return fallbackAImagenVoucher(voucher);
    }

    private Mono<String> fallbackAImagenVoucher(VoucherDocument voucher) {
        String path = voucher.getImagenPath();
        if (path == null || path.isBlank()) {
            return Mono.error(new BadRequestException(
                    "No hay documento disponible para enviar (sin imagen ni comprobante)."));
        }
        log.debug("[EnviarComprobanteWA] Usando imagen original del voucher: {}", path);
        return Mono.just(path);
    }

    private String construirMensaje(String clienteNombre, String url) {
        String nombre = clienteNombre != null ? clienteNombre : "cliente";
        return String.format(
                "Hola %s, tu pago ha sido registrado correctamente ✅\n\nAquí tu comprobante:\n%s\n\n_(Enlace válido por 7 días)_",
                nombre, url);
    }
}
