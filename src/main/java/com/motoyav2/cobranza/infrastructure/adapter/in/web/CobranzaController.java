package com.motoyav2.cobranza.infrastructure.adapter.in.web;

import com.motoyav2.cobranza.application.dto.*;
import com.motoyav2.cobranza.application.port.in.*;
import com.motoyav2.cobranza.application.port.in.command.*;
import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.service.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosFiadorDocument;
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

    @GetMapping("/api/v1/cobranzas/dashboard")
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

    @GetMapping("/api/v1/cobranzas/casos")
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

    @GetMapping("/api/v1/cobranzas/casos/{contratoId}/vista360")
    public Mono<Vista360CasoDto> getVista360(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /casos/{contratoId}/vista360 contratoId={}", contratoId);
        return obtenerCasoUseCase.ejecutar(contratoId);
    }

    @GetMapping("/api/v1/cobranzas/casos/urgentes")
    public Flux<CasoResumenDto> getCasosUrgentes(
            @RequestParam(defaultValue = "10") int limit,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /casos/urgentes storeId={} limit={}", storeId, limit);
        ListarCasosQuery query = new ListarCasosQuery(storeId, "INTERVENCION_REQUERIDA", "CRITICA", null, 0, limit);
        return listarCasosUseCase.ejecutar(query);
    }

    // =========================================================================
    // INICIAR CASO
    // =========================================================================

    @PostMapping("/api/v1/cobranzas/iniciar")
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

        DatosFiadorDocument fiador = request.fiador() != null
                ? DatosFiadorDocument.builder()
                        .nombres(request.fiador().nombres())
                        .apellidos(request.fiador().apellidos())
                        .tipoDocumento(request.fiador().tipoDocumento())
                        .numeroDocumento(request.fiador().numeroDocumento())
                        .telefono(request.fiador().telefono())
                        .email(request.fiador().email())
                        .parentesco(request.fiador().parentesco())
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
                fiador,
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

    @PostMapping("/api/v1/cobranzas/importar-calendario")
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

    @GetMapping("/api/v1/cobranzas/casos/{contratoId}/promesas")
    public Flux<PromesaDocument> getPromesas(
            @PathVariable String contratoId,
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {

        log.debug("GET /casos/{contratoId}/promesas contratoId={} estado={}", contratoId, estado);
        return obtenerCasoUseCase.ejecutar(contratoId)
                .flatMapMany(v -> Flux.fromIterable(v.promesas() != null ? v.promesas() : List.of()))
                .filter(p -> estado == null || estado.equalsIgnoreCase(p.getEstado()));
    }

    @PostMapping("/api/v1/cobranzas/casos/{contratoId}/promesas")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> registrarPromesa(
            @PathVariable String contratoId,
            @RequestBody RegistrarPromesaRequest body,
            ServerWebExchange exchange) {

        String userId      = (String) exchange.getAttributes().get("userId");
        String userNombre  = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /casos/{contratoId}/promesas contratoId={} userId={}", contratoId, userId);

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

    @PostMapping("/api/v1/cobranzas/{contratoId}/asignar-agente")
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

    @GetMapping("/api/v1/cobranzas/vouchers")
    public Flux<VoucherDocument> getVouchers(
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /vouchers storeId={} estado={}", storeId, estado);
        return listarVouchersUseCase.ejecutar(storeId, estado);
    }

    @PostMapping(value = "/api/v1/cobranzas/vouchers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @PostMapping("/api/v1/cobranzas/vouchers/{id}/aprobar")
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

    @PostMapping("/api/v1/cobranzas/vouchers/{id}/rechazar")
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

    @GetMapping("/api/v1/cobranzas/comprobantes")
    public Flux<ComprobantePagoDocument> getComprobantes(
            @RequestParam(required = false) String contratoId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /comprobantes storeId={} contratoId={} tipo={} estado={}", storeId, contratoId, tipo, estado);
        return comprobantesService.listar(storeId, contratoId, tipo, estado, fechaDesde, fechaHasta);
    }

    @GetMapping("/api/v1/cobranzas/comprobantes/{id}")
    public Mono<ComprobantePagoDocument> getComprobante(
            @PathVariable String id,
            ServerWebExchange exchange) {
        log.debug("GET /comprobantes/{id} id={}", id);
        return comprobantesService.findById(id);
    }

    /**
     * POST /api/v1/cobranzas/comprobantes/generar
     * Genera un comprobante ejecutando el flujo de aprobación de un voucher existente.
     */
    @PostMapping("/api/v1/cobranzas/comprobantes/generar")
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

    @GetMapping("/api/v1/cobranzas/comprobantes/{id}/pdf")
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

    @PostMapping("/api/v1/cobranzas/comprobantes/{id}/anular")
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

    @GetMapping("/api/v1/cobranzas/{contratoId}/eventos")
    public Flux<EventoCobranzaDocument> getEventos(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /{contratoId}/eventos contratoId={}", contratoId);
        return eventoService.listar(contratoId);
    }

    @PostMapping("/api/v1/cobranzas/{contratoId}/eventos")
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

    @GetMapping("/api/v1/cobranzas/{contratoId}/movimientos")
    public Mono<MovimientosResumenDto> getMovimientos(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /{contratoId}/movimientos contratoId={}", contratoId);
        return movimientosService.listar(contratoId);
    }

    // =========================================================================
    // ALERTAS — static sub-paths BEFORE path variable patterns
    // =========================================================================

    @GetMapping("/api/v1/cobranzas/alertas/resumen")
    public Mono<AlertasResumenDto> getAlertasResumen(ServerWebExchange exchange) {
        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("GET /alertas/resumen storeId={}", storeId);
        return dashboardService.getAlertasResumen(storeId, userId, rol);
    }

    @PostMapping("/api/v1/cobranzas/alertas/marcar-todas-leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> leerTodasAlertas(ServerWebExchange exchange) {
        String storeId = (String) exchange.getAttributes().get("storeId");
        String userId  = (String) exchange.getAttributes().get("userId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("POST /alertas/marcar-todas-leidas storeId={}", storeId);
        return alertaService.ejecutar(storeId, "AGENTE".equalsIgnoreCase(rol) ? userId : null)
                .flatMap(alerta -> alertaService.marcarLeida(alerta.getId()))
                .then();
    }

    @GetMapping("/api/v1/cobranzas/alertas")
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

    @PatchMapping("/api/v1/cobranzas/alertas/{id}/leer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> leerAlerta(
            @PathVariable String id,
            ServerWebExchange exchange) {
        log.debug("PATCH /alertas/{id}/leer id={}", id);
        return alertaService.marcarLeida(id);
    }

    @DeleteMapping("/api/v1/cobranzas/alertas/{id}")
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

    @GetMapping("/api/v1/cobranzas/whatsapp/plantillas")
    public Flux<PlantillaWhatsappDocument> getPlantillas(ServerWebExchange exchange) {
        log.debug("GET /whatsapp/plantillas");
        return whatsappService.listarPlantillas();
    }

    @PostMapping("/api/v1/cobranzas/whatsapp/preview")
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

    @PostMapping("/api/v1/cobranzas/whatsapp/enviar")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> enviarWhatsapp(
            @RequestBody EnviarWhatsappRequest body,
            ServerWebExchange exchange) {

        String storeId    = (String) exchange.getAttributes().get("storeId");
        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("POST /whatsapp/enviar contratoId={} plantillaId={}", body.contratoId(), body.plantillaId());

        EnviarMensajeWhatsappCommand command = new EnviarMensajeWhatsappCommand(
                body.contratoId(), body.plantillaId(), body.variablesValores(),
                userId, userNombre, storeId, body.telefonoDestino());

        return enviarMensajeWhatsappUseCase.ejecutar(command)
                .map(mensajeId -> Map.<String, Object>of(
                        "status", "OK",
                        "message", "Mensaje enviado",
                        "mensajeId", mensajeId
                ));
    }

    @GetMapping("/api/v1/cobranzas/casos/{contratoId}/whatsapp")
    public Flux<MensajeWhatsappDocument> getHistorialWhatsapp(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("GET /casos/{contratoId}/whatsapp contratoId={}", contratoId);
        return whatsappService.listarMensajes(contratoId);
    }

    // =========================================================================
    // LLAMADAS (stub)
    // =========================================================================

    @PostMapping("/api/v1/cobranzas/{contratoId}/llamar")
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

    @GetMapping("/api/v1/cobranzas/estrategias")
    public Flux<EstrategiaDocument> getEstrategias(ServerWebExchange exchange) {
        log.debug("GET /estrategias");
        return estrategiaService.listar();
    }

    @PostMapping("/api/v1/cobranzas/estrategias")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EstrategiaDocument> crearEstrategia(
            @RequestBody EstrategiaDocument body,
            ServerWebExchange exchange) {

        log.debug("POST /estrategias nombre={}", body.getNombre());
        return estrategiaService.crear(body);
    }

    @PutMapping("/api/v1/cobranzas/estrategias/{id}")
    public Mono<EstrategiaDocument> actualizarEstrategia(
            @PathVariable String id,
            @RequestBody ActualizarEstrategiaRequest body,
            ServerWebExchange exchange) {

        String userId = (String) exchange.getAttributes().get("userId");
        log.debug("PUT /estrategias/{id} id={} activo={}", id, body.activo());

        return estrategiaService.actualizar(id, body.activo(), body.mensaje(), body.frecuenciaDias(), userId);
    }

    @DeleteMapping("/api/v1/cobranzas/estrategias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> eliminarEstrategia(
            @PathVariable String id,
            ServerWebExchange exchange) {

        log.debug("DELETE /estrategias/{id} id={}", id);
        return estrategiaService.eliminar(id);
    }

    @PostMapping("/api/v1/cobranzas/estrategias/{id}/disparar-manual")
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
     * POST /webhooks/whatsapp
     * Meta Cloud API envía status updates como JSON.
     * Payload ejemplo:
     * { "entry": [{ "changes": [{ "value": { "statuses": [{ "id": "wamid.xxx", "status": "delivered" }] } }] }] }
     */
    @PostMapping(value = "/webhooks/whatsapp",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> metaWhatsappWebhook(
            @RequestBody Map<String, Object> payload) {

        log.debug("Meta WA webhook received");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
            if (entries == null || entries.isEmpty()) {
                return Mono.just(Map.of("status", "OK"));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entries.get(0).get("changes");
            if (changes == null || changes.isEmpty()) {
                return Mono.just(Map.of("status", "OK"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
            if (value == null) {
                return Mono.just(Map.of("status", "OK"));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> statuses = (List<Map<String, Object>>) value.get("statuses");
            if (statuses == null || statuses.isEmpty()) {
                return Mono.just(Map.of("status", "OK"));
            }

            Map<String, Object> statusEntry = statuses.get(0);
            String wamid  = (String) statusEntry.get("id");
            String status = (String) statusEntry.get("status");
            String estado = mapMetaStatus(status);
            Date timestamp = new Date();

            log.debug("Meta WA webhook wamid={} status={} -> estado={}", wamid, status, estado);

            return actualizarEstadoMensajeUseCase.ejecutar(wamid, estado, timestamp)
                    .thenReturn(Map.<String, Object>of("status", "OK"));
        } catch (Exception e) {
            log.warn("Error procesando Meta webhook: {}", e.getMessage());
            return Mono.just(Map.of("status", "OK"));
        }
    }

    /** GET /webhooks/whatsapp — verificación del webhook Meta (challenge) */
    @GetMapping("/webhooks/whatsapp")
    public Mono<String> metaWhatsappVerify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        log.debug("Meta WA webhook verify mode={}", mode);
        return Mono.just(challenge);
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

    private String mapMetaStatus(String metaStatus) {
        if (metaStatus == null) return "ENVIADO";
        return switch (metaStatus.toLowerCase()) {
            case "delivered" -> "ENTREGADO";
            case "read"      -> "LEIDO";
            case "sent"      -> "ENVIADO";
            case "failed"    -> "FALLIDO";
            default          -> "ENVIADO";
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
            String contratoId,
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

    public record DatosFiadorRequest(
            String nombres,
            String apellidos,
            String tipoDocumento,
            String numeroDocumento,
            String telefono,
            String email,
            String parentesco
    ) {}

    public record IniciarCasoRequest(
            String contratoId,
            DatosTitularRequest titular,
            DatosFiadorRequest fiador,
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
