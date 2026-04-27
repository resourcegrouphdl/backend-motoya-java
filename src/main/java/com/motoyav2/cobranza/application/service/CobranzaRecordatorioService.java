package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.AlertaCobranzaPort;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.domain.NivelMoraCalculadora;
import com.motoyav2.cobranza.domain.enums.NivelAlerta;
import com.motoyav2.cobranza.domain.enums.TipoAlerta;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Responsable único del envío de recordatorios WhatsApp automáticos a clientes.
 *
 * Reglas de negocio diarias (07:30 Lima vía CobranzaSchedulerOrchestrator):
 *
 *   Día -1 (vence mañana)   → recordatorio-cuota  [amistoso, sin mora]
 *   Día  0 (vence HOY)      → recordatorio-cuota  [amistoso, sin mora]
 *   Días 1–2 post-vencimiento → SILENCIO            [no spam]
 *   Día 3+                  → cuota-vencida        [con mora, cadencia ≥ 3 días]
 *
 *   Promesa VENCE HOY       → recordatorio de promesa
 *   Promesa INCUMPLIDA      → marcar + crear alerta operativa en cobranzas-alertas
 *   Voucher PENDIENTE       → pausa alertas de mora (revisión en curso)
 *
 * Todo WA enviado se persiste en cobranzas-mensajes-whatsapp (ventana 360°).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CobranzaRecordatorioService {

    private static final DateTimeFormatter DATE_FMT          = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int               CADENCIA_MORA_DIAS = 3;
    private static final int               SILENCIO_HASTA     = 2;  // días 1 y 2 → sin envío

    private final CasoCobranzaPort    casoPort;
    private final PromesaPort         promesaPort;
    private final VoucherPort         voucherPort;
    private final MensajeWhatsappPort mensajePort;
    private final AlertaCobranzaPort  alertaPort;
    private final NotificationFacade  notificationFacade;

    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Void> procesarRecordatorios() {
        LocalDate hoy = LocalDate.now(NivelMoraCalculadora.LIMA);
        log.info("[RECORDATORIO] Procesando recordatorios para {}", hoy);

        return casoPort.findAll()
                .filter(c -> esCasoGestionable(c.getCicloVida()))
                .filter(c -> !Boolean.TRUE.equals(c.getContactoBloqueado()))
                .flatMap(caso -> procesarCaso(caso, hoy)
                        .onErrorResume(e -> {
                            log.warn("[RECORDATORIO] Caso {} omitido: {}",
                                    caso.getContratoId(), e.getMessage());
                            return Mono.empty();
                        }), 6)
                .then()
                .doOnSuccess(v -> log.info("[RECORDATORIO] Completado para {}", hoy));
    }

    // ─── Orquestación por caso ────────────────────────────────────────────────

    private Mono<Void> procesarCaso(CasoCobranzaDocument caso, LocalDate hoy) {
        String telefono = caso.getClienteTelefono();
        if (telefono == null || telefono.isBlank()) return Mono.empty();

        // Evaluar promesa primero — tiene prioridad sobre el flujo normal
        return promesaPort.findVigente(caso.getContratoId())
                .flatMap(promesa -> manejarPromesa(caso, promesa, hoy, telefono))
                .switchIfEmpty(
                        tieneVoucherPendiente(caso.getContratoId())
                                .flatMap(hayVoucher -> hayVoucher
                                        ? enviarSoloPreVencimiento(caso, hoy, telefono)
                                        : enviarSegunDia(caso, hoy, telefono))
                );
    }

    // ─── Lógica de promesas ───────────────────────────────────────────────────

    private Mono<Void> manejarPromesa(CasoCobranzaDocument caso, PromesaDocument promesa,
                                       LocalDate hoy, String telefono) {
        LocalDate fechaPromesa;
        try {
            fechaPromesa = LocalDate.parse(promesa.getFecha());
        } catch (Exception e) {
            log.warn("[RECORDATORIO] Fecha promesa inválida | contrato={}", caso.getContratoId());
            return enviarSegunDia(caso, hoy, telefono);
        }

        if (fechaPromesa.isBefore(hoy)) {
            // Promesa incumplida: marcar + alerta operativa + seguir con flujo normal de mora
            return marcarPromesaIncumplida(caso, promesa)
                    .then(enviarSegunDia(caso, hoy, telefono));
        }

        if (fechaPromesa.equals(hoy)) {
            // Hoy vence la promesa: recordatorio de pago
            return notificarRecordatorioPromesa(caso, promesa, telefono);
        }

        // Promesa futura activa: pausar alertas de mora, solo pre-vencimiento si aplica
        return enviarSoloPreVencimiento(caso, hoy, telefono);
    }

    private Mono<Void> marcarPromesaIncumplida(CasoCobranzaDocument caso, PromesaDocument promesa) {
        log.info("[RECORDATORIO] Promesa incumplida | contrato={} fecha={}",
                caso.getContratoId(), promesa.getFecha());

        promesa.setEstado("INCUMPLIDA");
        promesa.setCerradaEn(new Date());
        promesa.setMotivoCierre("Fecha de compromiso vencida sin pago detectado");
        promesa.setActualizadoEn(new Date());

        caso.setEstadoCaso("PROMESA_INCUMPLIDA");
        caso.setActualizadoEn(new Date());

        AlertaCobranzaDocument alerta = AlertaCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .tipo(TipoAlerta.PROMESA_INCUMPLIDA.name())
                .nivel(NivelAlerta.WARNING.name())
                .titulo("Promesa incumplida: " + caso.getClienteNombre())
                .descripcion("El cliente no pagó en la fecha prometida: " + promesa.getFecha())
                .contratoId(caso.getContratoId())
                .clienteNombre(caso.getClienteNombre())
                .storeId(caso.getStoreId())
                .agenteId(caso.getAgenteAsignadoId())
                .accionSugerida("Contactar al cliente y registrar nueva gestión")
                .accionRuta("/cobranzas/vista360/" + caso.getContratoId())
                .leida(false)
                .descartada(false)
                .creadoEn(new Date())
                .expiraEn(new Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
                .build();

        return promesaPort.save(caso.getContratoId(), promesa)
                .then(casoPort.save(caso))
                .then(alertaPort.save(alerta).then())
                .onErrorResume(e -> {
                    log.warn("[RECORDATORIO] Error marcando promesa incumplida | contrato={}: {}",
                            caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> notificarRecordatorioPromesa(CasoCobranzaDocument caso,
                                                     PromesaDocument promesa, String telefono) {
        log.info("[RECORDATORIO] Promesa vence hoy | contrato={}", caso.getContratoId());
        caso.setEstadoCaso("PROMESA_VENCE_HOY");
        caso.setActualizadoEn(new Date());

        String montoFmt = NivelMoraCalculadora.fmt(promesa.getMonto() != null ? promesa.getMonto() : 0.0);

        return casoPort.save(caso)
                .then(notificationFacade.notificarRecordatorioPromesa(
                        caso.getContratoId(), telefono, caso.getClienteNombre(), montoFmt)
                        .onErrorResume(e -> Mono.empty()))
                .then(guardarEnVentana360(caso, "Recordatorio promesa de pago", null))
                .then();
    }

    // ─── Envío según día ─────────────────────────────────────────────────────

    /** Pre-vencimiento (día -1) sin alertas de mora. Para uso cuando hay promesa/voucher vigente. */
    private Mono<Void> enviarSoloPreVencimiento(CasoCobranzaDocument caso,
                                                 LocalDate hoy, String telefono) {
        return enviarPreVencimientoSiCorresponde(caso, hoy, telefono);
    }

    /** Flujo normal sin obstáculos: pre-venc + día0 + silencio/mora según los días. */
    private Mono<Void> enviarSegunDia(CasoCobranzaDocument caso, LocalDate hoy, String telefono) {
        Mono<Void> preVenc = enviarPreVencimientoSiCorresponde(caso, hoy, telefono);
        Mono<Void> dia0    = enviarDia0SiCorresponde(caso, hoy, telefono);

        // Evaluar mora DESPUÉS de que día0 haya actualizado ultimoRecordatorioMora en memoria
        Mono<Void> mora = Mono.defer(() -> {
            int dm = NivelMoraCalculadora.diasMora(caso, hoy);
            if (dm <= 0 || dm <= SILENCIO_HASTA) return Mono.empty();
            if (dm >= CADENCIA_MORA_DIAS && debeMandarse(caso, hoy)) {
                return enviarAlertaMora(caso, hoy, telefono);
            }
            return Mono.empty();
        });

        return preVenc.then(dia0).then(mora);
    }

    // ── Día -1: cuota vence mañana ────────────────────────────────────────────

    private Mono<Void> enviarPreVencimientoSiCorresponde(CasoCobranzaDocument caso,
                                                          LocalDate hoy, String telefono) {
        LocalDate manana = hoy.plusDays(1);
        CuotaCronogramaDocument cuota = NivelMoraCalculadora.proximaCuotaEnRango(caso, manana, manana);
        if (cuota == null) return Mono.empty();

        LocalDate fechaVenc = LocalDate.parse(cuota.getFechaVencimiento());
        String monto  = NivelMoraCalculadora.fmt(cuota.getMonto() != null ? cuota.getMonto() : 0.0);
        String fechaF = fechaVenc.format(DATE_FMT);

        log.info("[RECORDATORIO] Pre-vencimiento día -1 | contrato={} vence={}",
                caso.getContratoId(), fechaF);

        return notificationFacade.notificarRecordatorioCuota(
                        caso.getContratoId(), telefono, caso.getClienteNombre(), monto, fechaF)
                .onErrorResume(e -> {
                    log.warn("[RECORDATORIO] WA pre-venc fallido {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                })
                .then(guardarEnVentana360(caso, "Recordatorio cuota vence mañana", null))
                .then();
    }

    // ── Día 0: cuota vence HOY (sin mora) ─────────────────────────────────────

    private Mono<Void> enviarDia0SiCorresponde(CasoCobranzaDocument caso,
                                                LocalDate hoy, String telefono) {
        if (!NivelMoraCalculadora.tienesCuotaQueVenceHoy(caso, hoy)) return Mono.empty();
        if (yaSeMandoHoy(caso, hoy)) return Mono.empty();

        double montoCuota = NivelMoraCalculadora.montoPrimeraCuotaImpaga(caso, hoy);
        String montoFmt   = NivelMoraCalculadora.fmt(montoCuota);
        String fechaFmt   = hoy.format(DATE_FMT);

        log.info("[RECORDATORIO] Día 0 — cuota vence hoy | contrato={}", caso.getContratoId());

        return notificationFacade.notificarRecordatorioCuota(
                        caso.getContratoId(), telefono, caso.getClienteNombre(), montoFmt, fechaFmt)
                .onErrorResume(e -> {
                    log.warn("[RECORDATORIO] WA día 0 fallido {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                })
                .then(marcarUltimoRecordatorio(caso))
                .then(guardarEnVentana360(caso, "Recordatorio cuota vence hoy", null))
                .then();
    }

    // ── Día 3+: alerta de mora ─────────────────────────────────────────────────

    private Mono<Void> enviarAlertaMora(CasoCobranzaDocument caso,
                                         LocalDate hoy, String telefono) {
        int diasMora    = NivelMoraCalculadora.diasMora(caso, hoy);
        double cuota    = NivelMoraCalculadora.montoPrimeraCuotaImpaga(caso, hoy);
        double mora     = NivelMoraCalculadora.moraSoles(diasMora);
        double total    = cuota + mora;

        log.info("[RECORDATORIO] Alerta mora | contrato={} dias={}", caso.getContratoId(), diasMora);

        return notificationFacade.notificarCuotaVencida(
                        caso.getContratoId(), telefono, caso.getClienteNombre(),
                        NivelMoraCalculadora.fmt(cuota),
                        String.valueOf(diasMora),
                        NivelMoraCalculadora.fmt(mora),
                        NivelMoraCalculadora.fmt(total))
                .onErrorResume(e -> {
                    log.warn("[RECORDATORIO] WA mora fallido {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                })
                .then(marcarUltimoRecordatorio(caso))
                .then(guardarEnVentana360(caso, "Alerta mora — " + diasMora + " días", "cuota-vencida"))
                .then();
    }

    // ─── Ventana 360 ──────────────────────────────────────────────────────────

    private Mono<Void> guardarEnVentana360(CasoCobranzaDocument caso,
                                            String plantillaNombre, String estrategiaId) {
        MensajeWhatsappDocument msg = MensajeWhatsappDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(caso.getContratoId())
                .clienteNombre(caso.getClienteNombre())
                .telefono(caso.getClienteTelefono())
                .plantillaNombre(plantillaNombre)
                .estado("ENVIADO")
                .direction("OUTBOUND")
                .automatico(true)
                .estrategiaId(estrategiaId)
                .enviadoEn(new Date())
                .storeId(caso.getStoreId())
                .build();

        return mensajePort.save(msg)
                .doOnError(e -> log.warn("[360] Error guardando en ventana 360 | contrato={}: {}",
                        caso.getContratoId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** true si ya se envió un recordatorio de mora HOY (anti-spam día 0). */
    private boolean yaSeMandoHoy(CasoCobranzaDocument caso, LocalDate hoy) {
        Date ultimo = caso.getUltimoRecordatorioMora();
        if (ultimo == null) return false;
        return ultimo.toInstant().atZone(NivelMoraCalculadora.LIMA).toLocalDate().equals(hoy);
    }

    /** true si han pasado al menos CADENCIA_MORA_DIAS desde el último recordatorio. */
    private boolean debeMandarse(CasoCobranzaDocument caso, LocalDate hoy) {
        Date ultimo = caso.getUltimoRecordatorioMora();
        if (ultimo == null) return true;
        LocalDate ultimaFecha = ultimo.toInstant().atZone(NivelMoraCalculadora.LIMA).toLocalDate();
        return ChronoUnit.DAYS.between(ultimaFecha, hoy) >= CADENCIA_MORA_DIAS;
    }

    private Mono<Void> marcarUltimoRecordatorio(CasoCobranzaDocument caso) {
        caso.setUltimoRecordatorioMora(new Date());
        caso.setActualizadoEn(new Date());
        return casoPort.save(caso).then();
    }

    private Mono<Boolean> tieneVoucherPendiente(String contratoId) {
        return voucherPort.findByContratoId(contratoId)
                .filter(v -> "PENDIENTE".equals(v.getEstado()))
                .hasElements();
    }

    private boolean esCasoGestionable(String cicloVida) {
        if (cicloVida == null) return true;
        return switch (cicloVida) {
            case "PAGADO_TOTAL", "CASTIGADO", "CERRADO", "CANCELADO" -> false;
            default -> true;
        };
    }
}