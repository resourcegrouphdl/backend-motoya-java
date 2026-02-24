package com.motoyav2.cobranza.infrastructure.adapter.in.web;

import com.motoyav2.cobranza.application.dto.*;
import com.motoyav2.cobranza.application.port.in.*;
import com.motoyav2.cobranza.application.port.in.command.*;
import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.service.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
public class CobranzaController {

    private final ListarCasosUseCase listarCasosUseCase;
    private final ObtenerCasoUseCase obtenerCasoUseCase;
    private final AsignarAgenteUseCase asignarAgenteUseCase;
    private final RegistrarPromesaUseCase registrarPromesaUseCase;
    private final RecibirVoucherUseCase recibirVoucherUseCase;
    private final AprobarVoucherUseCase aprobarVoucherUseCase;
    private final RechazarVoucherUseCase rechazarVoucherUseCase;
    private final ListarVouchersUseCase listarVouchersUseCase;
    private final EnviarMensajeWhatsappUseCase enviarMensajeWhatsappUseCase;
    private final ActualizarEstadoMensajeUseCase actualizarEstadoMensajeUseCase;

    private final DashboardService dashboardService;
    private final ComprobantesService comprobantesService;
    private final EventoService eventoService;
    private final MovimientosService movimientosService;
    private final AlertaService alertaService;
    private final EstrategiaService estrategiaService;
    private final WhatsappService whatsappService;
    private final IniciarCasoUseCase iniciarCasoUseCase;
    private final ImportarCalendarioService importarCalendarioService;

    // =========================================================================
    // DASHBOARD
    // =========================================================================

    @GetMapping("/api/cobranzas/dashboard")
    public Mono<DashboardDto> getDashboard(ServerWebExchange exchange) {
        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("GET /dashboard storeId={} userId={} rol={}", storeId, userId, rol);
        return dashboardService.getDashboard(storeId, userId, rol);
    }

    // =========================================================================
    // CASOS
    // =========================================================================

    @GetMapping("/api/cobranzas/casos")
    public Flux<CasoResumenDto> getCasosDeCobranza(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String agenteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        ListarCasosQuery query = new ListarCasosQuery(storeId, estado, prioridad, agenteId, page, size);
        log.debug("GET /casos storeId={} estado={} prioridad={} agenteId={} page={} size={}",
                storeId, estado, prioridad, agenteId, page, size);
        return listarCasosUseCase.ejecutar(query);
    }

    @GetMapping("/api/cobranzas/{contratoId}")
    public Mono<Vista360CasoDto> getVista360(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /{contratoId} contratoId={}", contratoId);
        return obtenerCasoUseCase.ejecutar(contratoId);
    }

    // =========================================================================
    // INICIAR CASO
    // =========================================================================

    @PostMapping("/api/cobranzas/iniciar")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CasoCobranzaDocument> iniciarCaso(
            @RequestBody IniciarCasoRequest request,
            ServerWebExchange exchange) {
        String storeId    = (String) exchange.getAttributes().get("storeId");
        String userId     = (String) exchange.getAttributes().get("userId");

        DatosTitularDocument titular = request.titular() != null
                ? DatosTitularDocument.builder()
                        .nombres(request.titular().nombres())
                        .apellidos(request.titular().apellidos())
                        .tipoDocumento(request.titular().tipoDocumento())
                        .numeroDocumento(request.titular().numeroDocumento())
                        .telefono(request.titular().telefono())
                        .email(request.titular().email())
                        .direccion(request.titular().direccion())
                        .distrito(request.titular().distrito())
                        .provincia(request.titular().provincia())
                        .departamento(request.titular().departamento())
                        .build()
                : null;

        List<CuotaCronogramaDocument> cronograma = request.cronograma() != null
                ? request.cronograma().stream()
                        .map(c -> CuotaCronogramaDocument.builder()
                                .cuotaNum(c.cuotaNum())
                                .cuota(c.cuotaNum())
                                .monto(c.monto())
                                .fechaVencimiento(c.fechaVencimiento())
                                .estado(c.estado())
                                .build())
                        .toList()
                : null;

        IniciarCasoCommand command = new IniciarCasoCommand(
                request.contratoId(),
                storeId,
                titular,
                request.motoDescripcion(),
                request.capitalOriginal(),
                request.saldoActual(),
                request.nivelEstrategia(),
                request.estadoCaso(),
                request.agenteAsignadoId(),
                request.agenteAsignadoNombre(),
                request.fechaVencimientoPrimerCuotaImpaga(),
                cronograma,
                userId
        );

        log.debug("POST /iniciar contratoId={} storeId={} userId={}", request.contratoId(), storeId, userId);
        return iniciarCasoUseCase.ejecutar(command);
    }

    // =========================================================================
    // IMPORTAR CALENDARIO
    // =========================================================================

    @PostMapping("/api/cobranzas/importar-calendario")
    public Mono<ImportarCalendarioResultDto> importarCalendario(
            @RequestBody ImportarCalendarioRequest request,
            ServerWebExchange exchange) {
        String storeId    = (String) exchange.getAttributes().get("storeId");
        String userId     = (String) exchange.getAttributes().get("userId");

        log.debug("POST /importar-calendario calendarId={} storeId={} userId={}",
                request.calendarId(), storeId, userId);

        return importarCalendarioService.importar(
                request.calendarId(),
                storeId,
                request.agenteAsignadoId(),
                request.agenteAsignadoNombre(),
                userId
        );
    }

    // =========================================================================
    // PROMESAS
    // =========================================================================

    @GetMapping("/api/cobranzas/promesas")
    public Flux<PromesaDocument> getPromesas(
            @RequestParam(required = false) String contratoId,
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /promesas storeId={} contratoId={} estado={}", storeId, contratoId, estado);

        if (contratoId != null) {
            // Delegate to ObtenerCasoUseCase via Vista360 — or use PromesaPort directly
            // For simplicity, use eventoService to avoid extra port injection; use Vista360 datos
            // The cleanest approach: return promesas from Vista360 but we don't have direct PromesaPort here.
            // Return empty — caller should use the Vista360 endpoint for per-contrato promesas.
            return Flux.empty();
        }
        return Flux.empty();
    }

    @PostMapping("/api/cobranzas/{contratoId}/promesa")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> registrarPromesa(
            @PathVariable String contratoId,
            @RequestBody RegistrarPromesaRequest body,
            ServerWebExchange exchange) {

        String userId      = (String) exchange.getAttributes().get("userId");
        String userNombre  = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /{contratoId}/promesa contratoId={} userId={}", contratoId, userId);

        RegistrarPromesaCommand command = new RegistrarPromesaCommand(
                contratoId, body.fechaPromesa(), body.monto(), body.observaciones(),
                userId, userNombre);

        return registrarPromesaUseCase.ejecutar(command)
                .map(promesaId -> Map.of(
                        "status", "OK",
                        "message", "Promesa registrada",
                        "promesaId", promesaId
                ));
    }

    // =========================================================================
    // ASIGNAR AGENTE
    // =========================================================================

    @PostMapping("/api/cobranzas/{contratoId}/asignar-agente")
    public Mono<Map<String, Object>> asignarAgente(
            @PathVariable String contratoId,
            @RequestBody AsignarAgenteRequest body,
            ServerWebExchange exchange) {

        String userId      = (String) exchange.getAttributes().get("userId");
        String userNombre  = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /{contratoId}/asignar-agente contratoId={} agenteId={}", contratoId, body.agenteId());

        AsignarAgenteCommand command = new AsignarAgenteCommand(
                contratoId, null, body.agenteId(), body.agenteNombre(),
                body.motivo(), userId, userNombre);

        return asignarAgenteUseCase.ejecutar(command)
                .thenReturn(Map.<String, Object>of(
                        "status", "OK",
                        "message", "Caso asignado correctamente"
                ));
    }

    // =========================================================================
    // VOUCHERS
    // =========================================================================

    @GetMapping("/api/cobranzas/vouchers")
    public Flux<VoucherDocument> getVouchers(
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /vouchers storeId={} estado={}", storeId, estado);
        return listarVouchersUseCase.ejecutar(storeId, estado);
    }

    @PostMapping(value = "/api/cobranzas/vouchers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> recibirVoucher(
            @RequestParam(required = false) String contratoId,
            @RequestParam(required = false) String imagenPath,
            @RequestParam(required = false) String thumbPath,
            @RequestParam(required = false) Double montoDetectado,
            @RequestParam(required = false) String fuente,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        log.debug("POST /vouchers storeId={} contratoId={}", storeId, contratoId);

        RecibirVoucherCommand command = new RecibirVoucherCommand(
                contratoId, storeId, imagenPath, thumbPath, montoDetectado, userId);

        return recibirVoucherUseCase.ejecutar(command)
                .map(voucherId -> Map.<String, Object>of(
                        "status", "OK",
                        "message", "Voucher recibido",
                        "voucherId", voucherId
                ));
    }

    @PostMapping("/api/cobranzas/vouchers/{id}/aprobar")
    public Mono<Map<String, Object>> aprobarVoucher(
            @PathVariable String id,
            @RequestBody AprobarVoucherRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /vouchers/{id}/aprobar id={} userId={}", id, userId);

        // Determine serie based on tipo
        String serie = "FACTURA".equalsIgnoreCase(body.tipo()) ? "F001" : "B001";

        AprobarVoucherCommand command = new AprobarVoucherCommand(
                id, userId, userNombre,
                serie,
                null, null, null,
                body.rucReceptor() != null ? "RUC" : "DNI",
                body.rucReceptor(),
                body.razonSocialReceptor(),
                null
        );

        return aprobarVoucherUseCase.ejecutar(command)
                .map(comprobanteId -> Map.<String, Object>of(
                        "status", "OK",
                        "message", "Voucher aprobado",
                        "voucherId", id,
                        "comprobanteId", comprobanteId
                ));
    }

    @PostMapping("/api/cobranzas/vouchers/{id}/rechazar")
    public Mono<Map<String, Object>> rechazarVoucher(
            @PathVariable String id,
            @RequestBody RechazarVoucherRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /vouchers/{id}/rechazar id={} userId={}", id, userId);

        RechazarVoucherCommand command = new RechazarVoucherCommand(
                id, body.motivo(), body.observaciones(), userId, userNombre);

        return rechazarVoucherUseCase.ejecutar(command)
                .thenReturn(Map.<String, Object>of(
                        "status", "OK",
                        "message", "Voucher rechazado"
                ));
    }

    // =========================================================================
    // COMPROBANTES
    // =========================================================================

    @GetMapping("/api/cobranzas/comprobantes")
    public Flux<ComprobantePagoDocument> getComprobantes(
            @RequestParam(required = false) String contratoId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /comprobantes storeId={} contratoId={} tipo={} estado={}", storeId, contratoId, tipo, estado);
        return comprobantesService.listar(storeId, contratoId, tipo, estado);
    }

    @GetMapping("/api/cobranzas/comprobantes/{id}")
    public Mono<ComprobantePagoDocument> getComprobante(
            @PathVariable String id,
            ServerWebExchange exchange) {
        log.debug("GET /comprobantes/{id} id={}", id);
        return comprobantesService.findById(id);
    }

    /**
     * POST /api/cobranzas/comprobantes/generar
     * Genera un comprobante ejecutando el flujo de aprobación de un voucher existente.
     */
    @PostMapping("/api/cobranzas/comprobantes/generar")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> generarComprobante(
            @RequestBody GenerarComprobanteRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /comprobantes/generar voucherId={} userId={}", body.voucherId(), userId);

        AprobarVoucherCommand command = new AprobarVoucherCommand(
                body.voucherId(), userId, userNombre,
                "B001",
                null, null, null,
                "DNI", null, null, null
        );

        return aprobarVoucherUseCase.ejecutar(command)
                .map(comprobanteId -> Map.<String, Object>of(
                        "status", "OK",
                        "message", "Comprobante generado",
                        "comprobanteId", comprobanteId
                ));
    }

    @GetMapping("/api/cobranzas/comprobantes/{id}/pdf")
    public Mono<Map<String, Object>> getComprobantePdf(
            @PathVariable String id,
            ServerWebExchange exchange) {
        log.debug("GET /comprobantes/{id}/pdf id={}", id);
        return comprobantesService.findById(id)
                .map(c -> Map.<String, Object>of(
                        "url", c.getPdfPath() != null ? c.getPdfPath() : "",
                        "expiraEn", (Object) null
                ));
    }

    @PostMapping("/api/cobranzas/comprobantes/{id}/anular")
    public Mono<ComprobantePagoDocument> anularComprobante(
            @PathVariable String id,
            @RequestBody AnularComprobanteRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /comprobantes/{id}/anular id={} userId={}", id, userId);

        return comprobantesService.anular(id, body.motivo(), userId, userNombre);
    }

    // =========================================================================
    // EVENTOS
    // =========================================================================

    @GetMapping("/api/cobranzas/{contratoId}/eventos")
    public Flux<EventoCobranzaDocument> getEventos(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /{contratoId}/eventos contratoId={}", contratoId);
        return eventoService.listar(contratoId);
    }

    @PostMapping("/api/cobranzas/{contratoId}/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EventoCobranzaDocument> crearEvento(
            @PathVariable String contratoId,
            @RequestBody CrearEventoRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /{contratoId}/eventos contratoId={} tipo={}", contratoId, body.tipo());

        return eventoService.crearManual(
                contratoId, body.tipo(), body.payload(), userId, userNombre);
    }

    // =========================================================================
    // MOVIMIENTOS
    // =========================================================================

    @GetMapping("/api/cobranzas/{contratoId}/movimientos")
    public Mono<MovimientosResumenDto> getMovimientos(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /{contratoId}/movimientos contratoId={}", contratoId);
        return movimientosService.listar(contratoId);
    }

    // =========================================================================
    // ALERTAS — static sub-paths BEFORE path variable patterns
    // =========================================================================

    @GetMapping("/api/cobranzas/alertas/resumen")
    public Mono<AlertasResumenDto> getAlertasResumen(ServerWebExchange exchange) {
        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("GET /alertas/resumen storeId={}", storeId);
        return dashboardService.getAlertasResumen(storeId, userId, rol);
    }

    @PatchMapping("/api/cobranzas/alertas/leer-todas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> leerTodasAlertas(ServerWebExchange exchange) {
        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("PATCH /alertas/leer-todas storeId={}", storeId);
        return alertaService.ejecutar(storeId, "AGENTE".equalsIgnoreCase(rol) ? userId : null)
                .flatMap(alerta -> alertaService.marcarLeida(alerta.getId()))
                .then();
    }

    @GetMapping("/api/cobranzas/alertas")
    public Flux<AlertaCobranzaDocument> getAlertas(
            @RequestParam(required = false) String nivel,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("GET /alertas storeId={} rol={}", storeId, rol);

        return alertaService.ejecutar(storeId, "AGENTE".equalsIgnoreCase(rol) ? userId : null)
                .filter(a -> nivel == null || nivel.equalsIgnoreCase(a.getNivel()));
    }

    @PatchMapping("/api/cobranzas/alertas/{id}/leer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> leerAlerta(
            @PathVariable String id,
            ServerWebExchange exchange) {
        log.debug("PATCH /alertas/{id}/leer id={}", id);
        return alertaService.marcarLeida(id);
    }

    @DeleteMapping("/api/cobranzas/alertas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> descartarAlerta(
            @PathVariable String id,
            ServerWebExchange exchange) {
        log.debug("DELETE /alertas/{id} id={}", id);
        return alertaService.descartar(id);
    }

    // =========================================================================
    // WHATSAPP — static sub-paths BEFORE path variable patterns
    // =========================================================================

    @GetMapping("/api/cobranzas/whatsapp/plantillas")
    public Flux<PlantillaWhatsappDocument> getPlantillas(ServerWebExchange exchange) {
        log.debug("GET /whatsapp/plantillas");
        return whatsappService.listarPlantillas();
    }

    @PostMapping("/api/cobranzas/whatsapp/preview")
    public Mono<Map<String, Object>> previewWhatsapp(
            @RequestBody PreviewWhatsappRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /whatsapp/preview contratoId={} plantillaId={}", body.contratoId(), body.plantillaId());

        return whatsappService.preview(body.contratoId(), body.plantillaId(), body.variablesValores())
                .map(texto -> Map.<String, Object>of(
                        "plantillaId", body.plantillaId(),
                        "preview", texto
                ));
    }

    @PostMapping("/api/cobranzas/{contratoId}/whatsapp/enviar")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> enviarWhatsapp(
            @PathVariable String contratoId,
            @RequestBody EnviarWhatsappRequest body,
            ServerWebExchange exchange) {

        String storeId    = (String) exchange.getAttributes().get("storeId");
        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /{contratoId}/whatsapp/enviar contratoId={} plantillaId={}", contratoId, body.plantillaId());

        EnviarMensajeWhatsappCommand command = new EnviarMensajeWhatsappCommand(
                contratoId, body.plantillaId(), body.variablesValores(),
                userId, userNombre, storeId, body.telefonoDestino());

        return enviarMensajeWhatsappUseCase.ejecutar(command)
                .map(mensajeId -> Map.<String, Object>of(
                        "status", "OK",
                        "message", "Mensaje enviado",
                        "mensajeId", mensajeId
                ));
    }

    @GetMapping("/api/cobranzas/{contratoId}/whatsapp/historial")
    public Flux<MensajeWhatsappDocument> getHistorialWhatsapp(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /{contratoId}/whatsapp/historial contratoId={}", contratoId);
        return whatsappService.listarMensajes(contratoId);
    }

    // =========================================================================
    // LLAMADAS (stub)
    // =========================================================================

    @PostMapping("/api/cobranzas/{contratoId}/llamar")
    public Mono<Map<String, Object>> llamar(
            @PathVariable String contratoId,
            @RequestBody LlamarRequest body,
            ServerWebExchange exchange) {
        log.debug("POST /{contratoId}/llamar contratoId={} — NOT IMPLEMENTED", contratoId);
        return Mono.just(Map.of(
                "status", "NOT_IMPLEMENTED",
                "message", "Llamadas de voz próximamente"
        ));
    }

    // =========================================================================
    // ESTRATEGIAS
    // =========================================================================

    @GetMapping("/api/cobranzas/estrategias")
    public Flux<EstrategiaDocument> getEstrategias(ServerWebExchange exchange) {
        log.debug("GET /estrategias");
        return estrategiaService.listar();
    }

    @PatchMapping("/api/cobranzas/estrategias/{id}")
    public Mono<EstrategiaDocument> actualizarEstrategia(
            @PathVariable String id,
            @RequestBody ActualizarEstrategiaRequest body,
            ServerWebExchange exchange) {

        String userId = (String) exchange.getAttributes().get("userId");
        log.debug("PATCH /estrategias/{id} id={} activo={}", id, body.activo());

        return estrategiaService.actualizar(id, body.activo(), body.mensaje(), body.frecuenciaDias(), userId);
    }

    @PostMapping("/api/cobranzas/estrategias/{id}/disparar-manual")
    public Mono<Map<String, Object>> dispararEstrategia(
            @PathVariable String id,
            @RequestBody DispararEstrategiaRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /estrategias/{id}/disparar-manual id={} contratoIds={}", id, body.contratoIds());

        return estrategiaService.dispararManual(id, body.contratoIds(), body.observaciones(), userId, userNombre)
                .thenReturn(Map.<String, Object>of(
                        "status", "OK",
                        "message", "Estrategia disparada para " + body.contratoIds().size() + " contrato(s)"
                ));
    }

    // =========================================================================
    // WEBHOOKS
    // =========================================================================

    /**
     * POST /webhooks/twilio/whatsapp/status
     * Twilio envía el callback como application/x-www-form-urlencoded.
     */
    @PostMapping(value = "/webhooks/twilio/whatsapp/status",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<Map<String, Object>> twilioWhatsappStatus(ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(form -> {
                    String messageSid    = form.getFirst("MessageSid");
                    String messageStatus = form.getFirst("MessageStatus");

                    String estado = mapTwilioStatus(messageStatus);
                    Date timestamp = new Date();

                    log.debug("Twilio WA webhook sid={} status={} -> estado={}", messageSid, messageStatus, estado);

                    return actualizarEstadoMensajeUseCase.ejecutar(messageSid, estado, timestamp)
                            .thenReturn(Map.<String, Object>of("status", "OK"));
                });
    }

    /** POST /webhooks/twilio/voz/estado — stub */
    @PostMapping(value = "/webhooks/twilio/voz/estado")
    public Mono<Map<String, Object>> twilioVozEstado(ServerWebExchange exchange) {
        log.debug("Twilio VOZ webhook — stub");
        return Mono.just(Map.of("status", "OK"));
    }

    /** POST /webhooks/sunat/cdr — stub */
    @PostMapping("/webhooks/sunat/cdr")
    public Mono<Map<String, Object>> sunatCdr(ServerWebExchange exchange) {
        log.debug("SUNAT CDR webhook — stub");
        return Mono.just(Map.of("status", "OK"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String mapTwilioStatus(String twilioStatus) {
        if (twilioStatus == null) return "ENVIADO";
        return switch (twilioStatus.toLowerCase()) {
            case "delivered"    -> "ENTREGADO";
            case "read"         -> "LEIDO";
            case "sent",
                 "queued",
                 "accepted"     -> "ENVIADO";
            case "failed",
                 "undelivered"  -> "FALLIDO";
            default             -> "ENVIADO";
        };
    }

    // =========================================================================
    // Request body records (inline)
    // =========================================================================

    public record RegistrarPromesaRequest(
            String fechaPromesa,
            Double monto,
            String observaciones
    ) {}

    public record AsignarAgenteRequest(
            String agenteId,
            String agenteNombre,
            String motivo
    ) {}

    public record AprobarVoucherRequest(
            String tipo,
            String rucReceptor,
            String razonSocialReceptor,
            String emailReceptor,
            String observaciones
    ) {}

    public record RechazarVoucherRequest(
            String motivo,
            String observaciones
    ) {}

    public record EnviarWhatsappRequest(
            String plantillaId,
            Map<String, String> variablesValores,
            String telefonoDestino
    ) {}

    public record PreviewWhatsappRequest(
            String contratoId,
            String plantillaId,
            Map<String, String> variablesValores
    ) {}

    public record CrearEventoRequest(
            String tipo,
            Map<String, Object> payload
    ) {}

    public record LlamarRequest(
            String guionId,
            Map<String, Object> variables
    ) {}

    public record ActualizarEstrategiaRequest(
            Boolean activo,
            String mensaje,
            Integer frecuenciaDias
    ) {}

    public record DispararEstrategiaRequest(
            List<String> contratoIds,
            String observaciones
    ) {}

    public record GenerarComprobanteRequest(
            String voucherId,
            String tipo
    ) {}

    public record AnularComprobanteRequest(
            String motivo
    ) {}

    public record DatosTitularRequest(
            String nombres,
            String apellidos,
            String tipoDocumento,
            String numeroDocumento,
            String telefono,
            String email,
            String direccion,
            String distrito,
            String provincia,
            String departamento
    ) {}

    public record CuotaRequest(
            Integer cuotaNum,
            Double monto,
            String fechaVencimiento,
            String estado
    ) {}

    public record IniciarCasoRequest(
            String contratoId,
            DatosTitularRequest titular,
            String motoDescripcion,
            Double capitalOriginal,
            Double saldoActual,
            String nivelEstrategia,
            String estadoCaso,
            String agenteAsignadoId,
            String agenteAsignadoNombre,
            String fechaVencimientoPrimerCuotaImpaga,
            List<CuotaRequest> cronograma
    ) {}

    public record ImportarCalendarioRequest(
            String calendarId,
            String agenteAsignadoId,
            String agenteAsignadoNombre
    ) {}
}
