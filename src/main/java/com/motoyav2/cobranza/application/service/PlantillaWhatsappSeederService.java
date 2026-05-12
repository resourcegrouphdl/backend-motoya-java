package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.PlantillaWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PlantillaWhatsappDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.VariablePlantillaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.List;

/**
 * Siembra las plantillas de WhatsApp para cobranzas si la colección está vacía.
 * Se ejecuta al arranque después del seeder de estrategias (Order = 20).
 *
 * Las plantillas usan la sintaxis {{variable}} del motor interno de cobranzas.
 * Variables auto-cargadas por el frontend: nombre_cliente, dias_mora, deuda_total, monto_mora,
 *   monto_promesa, fecha_promesa.
 * Variables de entrada manual: numero_cuota, monto_cuota, monto_total.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class PlantillaWhatsappSeederService implements ApplicationRunner {

    private final PlantillaWhatsappPort plantillaPort;

    @Override
    public void run(ApplicationArguments args) {
        // Upsert siempre: garantiza que el contenido canónico (sin Yape) esté en Firestore
        log.info("[SEEDER-PLANTILLAS-WA] Sincronizando {} plantillas por defecto", plantillasPorDefecto().size());
        Flux.fromIterable(plantillasPorDefecto())
                .flatMap(plantillaPort::save)
                .subscribe(
                        p -> log.info("[SEEDER-PLANTILLAS-WA] Plantilla sincronizada: {}", p.getNombre()),
                        err -> log.error("[SEEDER-PLANTILLAS-WA] Error: {}", err.getMessage())
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plantillas por defecto
    // ─────────────────────────────────────────────────────────────────────────

    private List<PlantillaWhatsappDocument> plantillasPorDefecto() {
        Date ahora = new Date();
        return List.of(

            // 1 ── Recordatorio: cuota vence hoy ──────────────────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_recordatorio_dia_vencimiento")
                .nombre("Recordatorio — Cuota vence hoy")
                .categoria("RECORDATORIO_PAGO")
                .nivelMora(null)
                .cuerpo("""
                    Hola {{nombre_cliente}} 👋

                    *Motoya Digital* te recuerda que *hoy* vence tu cuota N° {{numero_cuota}}.

                    💰 *Monto a pagar:* S/ {{monto_cuota}}

                    Puedes pagar por transferencia o depósito:
                    🏦 *BCP:* 194-1058703-0-68
                       CCI: 002-19400105870306894
                    🏦 *Interbank:* 2003006691585
                       CCI: 00320000300669158538
                    🏦 *Scotiabank:* 000-4689149
                       CCI: 009-056-000004689149-35

                    📎 Envíanos el *comprobante* por este mismo chat y lo registramos al instante.

                    _Para hablar con un asesor: +51 912 301 507_""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente", "María García"),
                    var("numero_cuota",   "N° de cuota que vence hoy", "5"),
                    var("monto_cuota",    "Monto de la cuota (S/)", "250.00")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build(),

            // 2 ── Cuota vencida — mora temprana (1-15 días) ──────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_cuota_vencida_mora_temprana")
                .nombre("Cuota vencida — Mora temprana (1-15 días)")
                .categoria("MORA_TEMPRANA")
                .nivelMora("MORA_TEMPRANA")
                .cuerpo("""
                    ⚠️ Hola {{nombre_cliente}},

                    Tu cuota con *Motoya Digital* está vencida hace *{{dias_mora}} día(s)*.

                    💰 *Cuota pendiente:* S/ {{monto_cuota}}
                    📈 *Mora acumulada:* S/ {{monto_mora}}
                    📊 *Total a regularizar hoy:* S/ {{monto_total}}

                    Regulariza tu pago por transferencia o depósito:
                    🏦 *BCP:* 194-1058703-0-68
                       CCI: 002-19400105870306894
                    🏦 *Interbank:* 2003006691585
                       CCI: 00320000300669158538
                    🏦 *Scotiabank:* 000-4689149
                       CCI: 009-056-000004689149-35

                    📎 Envía tu *comprobante* por este chat y lo procesamos al instante.

                    _Para hablar con un asesor: +51 912 301 507_""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente",    "María García"),
                    var("dias_mora",      "Días en mora",          "5"),
                    var("monto_cuota",    "Monto de la cuota (S/)", "250.00"),
                    var("monto_mora",     "Mora acumulada (S/)",    "15.00"),
                    var("monto_total",    "Total a pagar (S/)",     "265.00")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build(),

            // 3 ── Aviso mora media (16-30 días) ─────────────────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_mora_media_aviso")
                .nombre("Aviso mora media (16-30 días)")
                .categoria("MORA_TEMPRANA")
                .nivelMora("MORA_MEDIA")
                .cuerpo("""
                    🔴 Hola {{nombre_cliente}},

                    Llevamos *{{dias_mora}} días* sin recibir tu pago con *Motoya Digital*.

                    💰 *Saldo pendiente:* S/ {{deuda_total}}
                    📈 *Mora acumulada:* S/ {{monto_mora}}

                    Es importante que te comuniques con nosotros hoy para coordinar el pago y evitar que tu caso escale a un tramo más grave.

                    📞 *Llámanos:* +51 912 301 507
                    📎 O envía tu comprobante por este mismo chat.

                    _Motoya Digital — Confianza en movimiento_ 🏍️""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente",    "María García"),
                    var("dias_mora",      "Días en mora",          "20"),
                    var("deuda_total",    "Saldo total pendiente (S/)", "750.00"),
                    var("monto_mora",     "Mora acumulada (S/)",    "60.00")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build(),

            // 4 ── Aviso mora crítica (31-60 días) ────────────────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_mora_critica_aviso")
                .nombre("Aviso mora crítica (31-60 días)")
                .categoria("MORA_CRITICA")
                .nivelMora("MORA_CRITICA")
                .cuerpo("""
                    🚨 *AVISO URGENTE* — {{nombre_cliente}}

                    Tu deuda con *Motoya Digital* lleva *{{dias_mora}} días* en mora.

                    💰 *Monto total adeudado:* S/ {{deuda_total}}

                    Si no regularizas tu situación en los próximos días, tu caso será derivado a *gestión pre-judicial*, lo que implicará costos adicionales.

                    Contáctanos *HOY* para llegar a un acuerdo de pago:
                    📞 *+51 912 301 507*
                    📎 O envía tu comprobante por este chat.

                    _Área de Cobranzas — Motoya Digital_""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente",    "María García"),
                    var("dias_mora",      "Días en mora",          "35"),
                    var("deuda_total",    "Monto total adeudado (S/)", "900.00")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build(),

            // 5 ── Confirmación de promesa de pago ────────────────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_promesa_confirmacion")
                .nombre("Confirmación de promesa de pago")
                .categoria("PROMESA_CONFIRMACION")
                .nivelMora(null)
                .cuerpo("""
                    ✅ Hola {{nombre_cliente}},

                    Confirmamos que hemos registrado tu promesa de pago:

                    📅 *Fecha acordada:* {{fecha_promesa}}
                    💰 *Monto comprometido:* S/ {{monto_promesa}}

                    El {{fecha_promesa}} un asesor realizará seguimiento. Cuando realices el pago, envíanos el comprobante por este mismo chat para cerrarlo de inmediato.

                    Puedes transferir o depositar a:
                    🏦 *BCP:* 194-1058703-0-68
                       CCI: 002-19400105870306894
                    🏦 *Interbank:* 2003006691585
                       CCI: 00320000300669158538
                    🏦 *Scotiabank:* 000-4689149
                       CCI: 009-056-000004689149-35

                    _¡Gracias por tu compromiso! — Motoya Digital_""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente",        "María García"),
                    var("fecha_promesa",  "Fecha de pago acordada",    "25/04/2025"),
                    var("monto_promesa",  "Monto prometido (S/)",      "265.00")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build(),

            // 6 ── Confirmación de voucher recibido ───────────────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_voucher_confirmacion")
                .nombre("Voucher recibido — en verificación")
                .categoria("VOUCHER_CONFIRMACION")
                .nivelMora(null)
                .cuerpo("""
                    ✅ Hola {{nombre_cliente}},

                    Recibimos tu comprobante de pago. Lo estamos verificando en este momento.

                    En breve un asesor confirmará el registro en tu cuenta y recibirás la confirmación final.

                    Si tienes alguna consulta:
                    📞 +51 912 301 507

                    _¡Gracias! — Motoya Digital_ 🏍️""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente", "María García")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build(),

            // 7 ── Aviso pre-judicial (61+ días) ──────────────────────────────
            PlantillaWhatsappDocument.builder()
                .id("plt_aviso_pre_judicial")
                .nombre("Aviso pre-judicial (61+ días)")
                .categoria("JUDICIAL_AVISO")
                .nivelMora("JUDICIAL")
                .cuerpo("""
                    ⚖️ *AVISO PRE-JUDICIAL*

                    Estimado(a) {{nombre_cliente}},

                    Su deuda con *Motoya Digital* por *S/ {{deuda_total}}* acumula *{{dias_mora}} días* de mora sin regularización.

                    Le comunicamos formalmente que, de no realizarse el pago o coordinar un acuerdo en las próximas *48 horas*, su caso será derivado al área legal para inicio de *proceso judicial*, lo que implicará costos adicionales de gestión y costas procesales.

                    Para evitar este proceso, contáctenos *URGENTE*:
                    📞 +51 912 301 507
                    ✉️ cobranzas@motoya.pe

                    _Área Legal — Motoya Digital_""")
                .variables(List.of(
                    var("nombre_cliente", "Nombre del cliente",         "María García"),
                    var("deuda_total",    "Monto total adeudado (S/)",  "1,200.00"),
                    var("dias_mora",      "Días en mora",               "65")
                ))
                .activa(true)
                .aprobadaPorMeta(true)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .creadoPor("SISTEMA")
                .build()
        );
    }

    private VariablePlantillaDocument var(String nombre, String descripcion, String valorEjemplo) {
        return VariablePlantillaDocument.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .valorEjemplo(valorEjemplo)
                .build();
    }
}
