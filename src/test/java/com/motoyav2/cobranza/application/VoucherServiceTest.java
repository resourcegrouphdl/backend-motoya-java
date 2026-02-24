package com.motoyav2.cobranza.application;

import com.motoyav2.cobranza.domain.enums.EstadoCuota;
import com.motoyav2.cobranza.domain.enums.EstadoVoucher;
import com.motoyav2.cobranza.domain.model.CuotaProvisional;
import com.motoyav2.cobranza.domain.model.HistorialContacto;
import com.motoyav2.cobranza.domain.model.VoucherPago;
import com.motoyav2.cobranza.domain.port.out.CuotaProvRepository;
import com.motoyav2.cobranza.domain.port.out.GoogleCalendarPort;
import com.motoyav2.cobranza.domain.port.out.HistorialContactoRepository;
import com.motoyav2.cobranza.domain.port.out.TwilioWhatsAppPort;
import com.motoyav2.cobranza.domain.port.out.VoucherPagoRepository;
import com.motoyav2.cobranza.domain.port.out.VoucherStoragePort;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock private CuotaProvRepository cuotaProvRepository;
    @Mock private VoucherPagoRepository voucherPagoRepository;
    @Mock private HistorialContactoRepository historialContactoRepository;
    @Mock private VoucherStoragePort voucherStoragePort;
    @Mock private TwilioWhatsAppPort twilioWhatsAppPort;
    @Mock private GoogleCalendarPort googleCalendarPort;
    @Mock private MoraCalculatorService moraCalculatorService;

    @InjectMocks
    private VoucherService voucherService;

    private VoucherPago voucherPendiente;
    private CuotaProvisional cuotaVoucherPendiente;

    @BeforeEach
    void setUp() {
        voucherPendiente = new VoucherPago(
                "voucher-001", "cuota-001", "contrato-001",
                "+51999888777", "SM123456", "vouchers/c/q/img.jpg",
                "https://firebasestorage.googleapis.com/img.jpg",
                "image/jpeg", EstadoVoucher.PENDIENTE, null, null,
                Instant.now(), null, Instant.now(), Instant.now()
        );

        cuotaVoucherPendiente = new CuotaProvisional(
                "cuota-001", "contrato-001", "MTD-CR-001",
                "Juan Pérez", "+51999888777", 3,
                Instant.now().minusSeconds(86400), // vencida ayer
                new BigDecimal("500.00"),
                EstadoCuota.VOUCHER_PENDIENTE, null, false, null,
                null, "cal-event-123", null,
                Instant.now(), Instant.now()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // aprobar() — camino feliz
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aprobar: actualiza voucher a APROBADO, cuota a PAGADA, sincroniza calendario y envía WhatsApp")
    void aprobar_deberiaAprobarVoucherYActualizarCuota() {
        // arrange
        when(voucherPagoRepository.findById("voucher-001"))
                .thenReturn(Mono.just(voucherPendiente));
        when(voucherPagoRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cuotaProvRepository.findById("cuota-001"))
                .thenReturn(Mono.just(cuotaVoucherPendiente));
        when(cuotaProvRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(historialContactoRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(googleCalendarPort.actualizarEvento(anyString(), any(int.class), anyString()))
                .thenReturn(Mono.empty());
        when(twilioWhatsAppPort.enviarMensaje(anyString(), anyString()))
                .thenReturn(Mono.empty());

        // act
        Mono<VoucherPago> result = voucherService.aprobar("voucher-001", "admin@motoya.pe");

        // assert
        StepVerifier.create(result)
                .assertNext(v -> {
                    assertThat(v.estadoValidacion()).isEqualTo(EstadoVoucher.APROBADO);
                    assertThat(v.validadoPor()).isEqualTo("admin@motoya.pe");
                    assertThat(v.fechaValidacion()).isNotNull();
                })
                .verifyComplete();

        verify(cuotaProvRepository).save(any(CuotaProvisional.class));
        verify(historialContactoRepository).save(any(HistorialContacto.class));
        verify(googleCalendarPort).actualizarEvento(eq("cal-event-123"), eq(2), anyString());
        verify(twilioWhatsAppPort).enviarMensaje(eq("+51999888777"), anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // aprobar() — voucher no encontrado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aprobar: lanza NotFoundException si el voucher no existe")
    void aprobar_lanzaNotFoundSiVoucherNoExiste() {
        when(voucherPagoRepository.findById("inexistente"))
                .thenReturn(Mono.empty());

        StepVerifier.create(voucherService.aprobar("inexistente", "admin@motoya.pe"))
                .expectError(NotFoundException.class)
                .verify();

        verify(voucherPagoRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // aprobar() — voucher ya procesado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aprobar: lanza BadRequestException si el voucher ya fue procesado")
    void aprobar_lanzaBadRequestSiVoucherYaProcesado() {
        VoucherPago aprobado = new VoucherPago(
                "voucher-001", "cuota-001", "contrato-001",
                "+51999888777", "SM123456", "path", "url",
                "image/jpeg", EstadoVoucher.APROBADO, null, "admin",
                Instant.now(), Instant.now(), Instant.now(), Instant.now()
        );

        when(voucherPagoRepository.findById("voucher-001"))
                .thenReturn(Mono.just(aprobado));

        StepVerifier.create(voucherService.aprobar("voucher-001", "admin@motoya.pe"))
                .expectError(BadRequestException.class)
                .verify();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rechazar() — camino feliz
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rechazar: actualiza voucher a RECHAZADO, restaura estado de cuota y envía WhatsApp")
    void rechazar_deberiaRechazarVoucherYRestaurarCuota() {
        when(voucherPagoRepository.findById("voucher-001"))
                .thenReturn(Mono.just(voucherPendiente));
        when(voucherPagoRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(cuotaProvRepository.findById("cuota-001"))
                .thenReturn(Mono.just(cuotaVoucherPendiente));
        when(cuotaProvRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(historialContactoRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(moraCalculatorService.calcularEstado(any()))
                .thenReturn(EstadoCuota.MORA);
        when(twilioWhatsAppPort.enviarMensaje(anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(voucherService.rechazar("voucher-001", "admin@motoya.pe", "Imagen borrosa"))
                .assertNext(v -> {
                    assertThat(v.estadoValidacion()).isEqualTo(EstadoVoucher.RECHAZADO);
                    assertThat(v.observacion()).isEqualTo("Imagen borrosa");
                    assertThat(v.validadoPor()).isEqualTo("admin@motoya.pe");
                })
                .verifyComplete();

        verify(moraCalculatorService).calcularEstado(any());
        verify(twilioWhatsAppPort).enviarMensaje(anyString(), anyString());
        // Google Calendar no debe actualizarse al rechazar
        verify(googleCalendarPort, never()).actualizarEvento(anyString(), any(int.class), anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // procesarWebhook() — sin media, debe ignorar
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("procesarWebhook: ignora mensajes sin archivos adjuntos")
    void procesarWebhook_ignoraMensajeSinMedia() {
        var command = new com.motoyav2.cobranza.domain.port.in.ProcesarVoucherUseCase.WebhookCommand(
                "SM001", "whatsapp:+51999888777", "Hola", 0, null, null
        );

        StepVerifier.create(voucherService.procesarWebhook(command))
                .verifyComplete();

        verify(voucherPagoRepository, never()).findByTwilioMessageSid(anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // procesarWebhook() — idempotencia
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("procesarWebhook: no duplica si el MessageSid ya fue procesado")
    void procesarWebhook_idempotenciaConSidDuplicado() {
        var command = new com.motoyav2.cobranza.domain.port.in.ProcesarVoucherUseCase.WebhookCommand(
                "SM-DUPLICADO", "whatsapp:+51999888777", "img", 1,
                "https://api.twilio.com/2010/media/SM-DUPLICADO/ME123",
                "image/jpeg"
        );

        when(voucherPagoRepository.findByTwilioMessageSid("SM-DUPLICADO"))
                .thenReturn(Mono.just(voucherPendiente));

        StepVerifier.create(voucherService.procesarWebhook(command))
                .verifyComplete();

        verify(voucherStoragePort, never()).downloadAndUpload(anyString(), anyString(), anyString());
    }
}
