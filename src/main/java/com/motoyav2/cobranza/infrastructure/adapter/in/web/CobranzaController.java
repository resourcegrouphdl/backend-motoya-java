package com.motoyav2.cobranza.infrastructure.adapter.in.web;

import com.motoyav2.cobranza.application.dto.*;
import com.motoyav2.cobranza.application.port.in.*;
import com.motoyav2.cobranza.application.port.in.command.*;
import com.motoyav2.cobranza.application.port.in.command.RegistrarPagoManualCommand;
import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.service.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.*;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosFiadorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import com.motoyav2.debug.DebugWaService;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
public class CobranzaController {

    private final ConsultarDuplicadosVoucherUseCase consultarDuplicadosVoucherUseCase;
    private final ListarCasosUseCase listarCasosUseCase;
    private final ObtenerCasoUseCase obtenerCasoUseCase;
    private final AsignarAgenteUseCase asignarAgenteUseCase;
    private final RegistrarPromesaUseCase registrarPromesaUseCase;
    private final CerrarPromesaUseCase cerrarPromesaUseCase;
    private final RecibirVoucherUseCase recibirVoucherUseCase;
    private final AprobarVoucherUseCase aprobarVoucherUseCase;
    private final RechazarVoucherUseCase rechazarVoucherUseCase;
    private final ListarVouchersUseCase listarVouchersUseCase;
    private final EnviarMensajeWhatsappUseCase enviarMensajeWhatsappUseCase;
    private final ActualizarEstadoMensajeUseCase actualizarEstadoMensajeUseCase;

    private final PromesaGlobalService promesaGlobalService;
    private final DashboardService dashboardService;
    private final RecalcularMetricasService recalcularMetricasService;
    private final ComprobantesService comprobantesService;
    private final EventoService eventoService;
    private final MovimientosService movimientosService;
    private final AlertaService alertaService;
    private final EstrategiaService estrategiaService;
    private final EstrategiaAutomaticaService estrategiaAutomaticaService;
    private final WhatsappService whatsappService;
    private final IniciarCasoUseCase iniciarCasoUseCase;
    private final ImportarCalendarioService importarCalendarioService;
    private final RegistrarPagoManualUseCase registrarPagoManualUseCase;
    private final ProcesarVoucherWhatsappService procesarVoucherWhatsappService;
    private final ReprocesarVoucherOcrService reprocesarVoucherOcrService;
    private final MoraDiariaService moraDiariaService;
    private final ConciliacionService conciliacionService;
    private final WhatsAppMediaStorageService mediaStorageService;
    private final DebugWaService debugWaService;

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

    @PostMapping("/api/v1/cobranzas/dashboard/recalcular")
    public Mono<Map<String, Object>> recalcularDashboard() {
        return recalcularMetricasService.recalcular()
                .map(doc -> Map.<String, Object>of(
                        "status", "OK",
                        "casosActivos", doc.getCasosActivos() != null ? doc.getCasosActivos() : 0,
                        "ultimaActualizacion", doc.getUltimaActualizacion() != null
                                ? doc.getUltimaActualizacion().toInstant().toString() : ""
                ));
    }

    // =========================================================================
    // MORA DIARIA — trigger manual para testing / emergencias
    // =========================================================================

    @PostMapping("/api/v1/cobranzas/mora/procesar")
    public Mono<Map<String, Object>> procesarMoraDiaria() {
        return moraDiariaService.procesarMora()
                .count()
                .map(total -> Map.<String, Object>of(
                        "status", "OK",
                        "casosActualizados", total
                ));
    }

    // =========================================================================
    // ESTRATEGIAS — trigger manual
    // =========================================================================

    @PostMapping("/api/v1/cobranzas/estrategias/ejecutar")
    public Mono<Map<String, Object>> ejecutarEstrategias() {
        return estrategiaAutomaticaService.ejecutarEstrategiasActivas()
                .map(total -> Map.<String, Object>of(
                        "status", "OK",
                        "casosContactados", total
                ));
    }

    // =========================================================================
    // CONCILIACIÓN
    // =========================================================================

    @GetMapping("/api/v1/cobranzas/conciliacion")
    public Mono<ConciliacionDto> getConciliacion() {
        return conciliacionService.conciliar();
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
        String rol     = (String) exchange.getAttributes().get("userRol");
        ListarCasosQuery query = new ListarCasosQuery(storeId, estado, prioridad, agenteId, rol, page, size);
        log.debug("GET /casos storeId={} rol={} estado={} prioridad={} agenteId={} page={} size={}",
                storeId, rol, estado, prioridad, agenteId, page, size);
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
        String rol     = (String) exchange.getAttributes().get("userRol");
        log.debug("GET /casos/urgentes storeId={} rol={} limit={}", storeId, rol, limit);
        ListarCasosQuery query = new ListarCasosQuery(storeId, "INTERVENCION_REQUERIDA", "CRITICA", null, rol, 0, limit);
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

    /** Lista global de promesas enriquecidas con datos del caso. */
    @GetMapping("/api/v1/cobranzas/promesas")
    public Flux<PromesaResumenDto> getPromesasGlobal(
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {
        String storeId = (String) exchange.getAttributes().get("storeId");
        log.debug("GET /promesas storeId={} estado={}", storeId, estado);
        return promesaGlobalService.listar(storeId, estado);
    }

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

    @PatchMapping("/api/v1/cobranzas/casos/{contratoId}/promesas/{promesaId}/cerrar")
    public Mono<Map<String, Object>> cerrarPromesa(
            @PathVariable String contratoId,
            @PathVariable String promesaId,
            @RequestBody CerrarPromesaRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.debug("PATCH /casos/{}/promesas/{}/cerrar resultado={}", contratoId, promesaId, body.resultado());

        CerrarPromesaCommand command = new CerrarPromesaCommand(
                contratoId, promesaId, body.resultado(),
                body.montoPagado(), body.motivo(),
                userId, userNombre);

        return cerrarPromesaUseCase.ejecutar(command)
                .thenReturn(Map.<String, Object>of(
                        "status", "OK",
                        "message", "Promesa cerrada"
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
    public Flux<VoucherResponse> getVouchers(
            @RequestParam(required = false) String estado,
            ServerWebExchange exchange) {

        String storeId = (String) exchange.getAttributes().get("storeId");
        String rol     = (String) exchange.getAttributes().get("userRol");
        // ADMIN ve vouchers de todas las tiendas (mismo patrón que /casos)
        String storeIdEfectivo = "ADMIN".equals(rol) ? null : storeId;
        log.debug("GET /vouchers storeId={} rol={} estado={}", storeId, rol, estado);

        return listarVouchersUseCase.ejecutar(storeIdEfectivo, estado)
                .flatMap(doc -> {
                    Mono<String> urlMono = (doc.getImagenPath() != null && !doc.getImagenPath().isBlank())
                            ? mediaStorageService.generarSignedUrl(doc.getImagenPath(), 15)
                            : Mono.just("");
                    return urlMono.map(url -> toVoucherResponse(doc, url));
                });
    }

    private VoucherResponse toVoucherResponse(VoucherDocument doc, String imagenUrl) {
        String fechaDeteccion = doc.getCreadoEn() != null
                ? doc.getCreadoEn().toInstant().toString()
                : null;
        return new VoucherResponse(
                doc.getId(), doc.getContratoId(), doc.getCliente(), doc.getClienteDni(),
                doc.getStoreId(), doc.getEstado(), doc.getFuente(),
                imagenUrl, doc.getImagenPath(),
                doc.getMontoDetectado(), doc.getMontoEsperado(), doc.getOcrResultado(),
                doc.getAprobadoPor(), doc.getAprobadoPorNombre(),
                doc.getRechazadoPor(), doc.getMotivoRechazo(), doc.getObservacionesRechazo(),
                doc.getComprobanteId(), fechaDeteccion, doc.getCreadoPor()
        );
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
                contratoId, storeId, imagenPath, thumbPath, montoDetectado,
                null, null, userId, "ADMIN_UPLOAD", null);

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

        // serie es null cuando no se elige tipo — el servicio omite la creación de comprobante
        String serie = body.tipo() == null ? null
                : ("FACTURA".equalsIgnoreCase(body.tipo()) ? "F001" : "B001");

        AprobarVoucherCommand command = new AprobarVoucherCommand(
                id, userId, userNombre,
                serie,
                null, null, null,
                body.rucReceptor() != null ? "RUC" : "DNI",
                body.rucReceptor(),
                body.razonSocialReceptor(),
                null,
                body.fechaPagoReal()
        );

        return aprobarVoucherUseCase.ejecutar(command)
                .map(comprobanteId -> {
                    java.util.LinkedHashMap<String, Object> resp = new java.util.LinkedHashMap<>();
                    resp.put("status",   "OK");
                    resp.put("message",  "Voucher aprobado");
                    resp.put("voucherId", id);
                    if (comprobanteId != null && !comprobanteId.isEmpty()) {
                        resp.put("comprobanteId", comprobanteId);
                    }
                    return (Map<String, Object>) resp;
                });
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

    @GetMapping("/api/v1/cobranzas/vouchers/{id}/contexto-duplicados")
    public Mono<ContextoDuplicadosDto> consultarDuplicados(@PathVariable String id) {
        log.debug("GET /vouchers/{}/contexto-duplicados", id);
        return consultarDuplicadosVoucherUseCase.ejecutar(id);
    }

    @PostMapping("/api/v1/cobranzas/vouchers/{id}/reprocesar-ocr")
    public Mono<Map<String, Object>> reprocesarVoucherOcr(
            @PathVariable String id,
            ServerWebExchange exchange) {

        log.info("POST /vouchers/{}/reprocesar-ocr userId={}", id,
                exchange.getAttributes().get("userId"));

        return reprocesarVoucherOcrService.ejecutar(id)
                .map(r -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("status", "OK");
                    resp.put("voucherId", r.voucherId());
                    resp.put("montoDetectadoAnterior", r.montoDetectadoAnterior());
                    resp.put("montoDetectadoNuevo", r.montoDetectadoNuevo());
                    resp.put("banco", r.banco());
                    resp.put("enriquecidoConLlm", r.enriquecidoConLlm());
                    resp.put("montoModificado", r.montoModificado());
                    resp.put("procesador", r.procesador());
                    resp.put("campos", r.campos());
                    return resp;
                })
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(Map.of("status", "ERROR", "message", e.getMessage())))
                .onErrorResume(IllegalStateException.class, e ->
                        Mono.just(Map.of("status", "ERROR", "message", e.getMessage())));
    }

    // =========================================================================
    // PAGO MANUAL — migración y correcciones administrativas
    // =========================================================================

    /**
     * Registra un pago de forma manual sin requerir comprobante digital.
     * Útil para:
     *   - Clientes migrados con pagos previos al sistema
     *   - Pagos en efectivo verificados por el agente
     *   - Corrección de registros históricos
     *
     * El endpoint marca las cuotas como PAGADA y actualiza el saldo.
     * Si se proporciona imagenPath (GCS path de imagen escaneada),
     * también crea un VoucherDocument con estado APROBADO para auditoría.
     */
    @PostMapping("/api/v1/cobranzas/casos/{contratoId}/pago-manual")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> registrarPagoManual(
            @PathVariable String contratoId,
            @RequestBody PagoManualRequest body,
            ServerWebExchange exchange) {

        String userId     = (String) exchange.getAttributes().get("userId");
        String userNombre = (String) exchange.getAttributes().get("userNombre");
        log.info("POST /casos/{contratoId}/pago-manual contratoId={} monto={} fechaPago={} fuente={}",
                contratoId, body.monto(), body.fechaPago(), body.fuente());

        RegistrarPagoManualCommand command = new RegistrarPagoManualCommand(
                contratoId,
                body.monto(),
                body.fechaPago(),
                body.numeroCuota(),
                body.observaciones(),
                body.imagenPath(),
                body.fuente(),
                userId,
                userNombre
        );

        return registrarPagoManualUseCase.ejecutar(command)
                .map(result -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("status",             "OK");
                    resp.put("message",            result.pendienteRevision()
                            ? "Comprobante recibido. Quedará pendiente de revisión para su aprobación."
                            : "Pago manual registrado correctamente.");
                    resp.put("contratoId",         result.contratoId());
                    resp.put("saldoNuevo",         result.saldoNuevo());
                    resp.put("cuotasMarcadas",     result.cuotasMarcadas());
                    resp.put("voucherId",          result.voucherId() != null ? result.voucherId() : "");
                    resp.put("pendienteRevision",  result.pendienteRevision());
                    return resp;
                });
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
                "DNI", null, null, null, null
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
                userId, userNombre, storeId, body.telefonoDestino(), body.mensajeLibre());

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

    @PatchMapping("/api/v1/cobranzas/casos/{contratoId}/whatsapp/marcar-leidos")
    public Mono<Map<String, Object>> marcarMensajesLeidos(
            @PathVariable String contratoId,
            ServerWebExchange exchange) {
        log.debug("PATCH /casos/{}/whatsapp/marcar-leidos", contratoId);
        return whatsappService.marcarMensajesLeidos(contratoId)
                .thenReturn(Map.<String, Object>of("status", "OK"));
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
     * Formato Factiliza: { event, instanceId, instanceName, data: { Info: {...}, Message: {...} } }
     * event="Message"  → mensaje entrante del cliente
     * event="Receipt"  → actualización de estado de entrega/lectura
     */
    @PostMapping(value = {"/webhooks/whatsapp", "/webhook/whatsapp"},
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> metaWhatsappWebhook(
            @RequestBody Map<String, Object> payload) {

        log.info("[WEBHOOK-WA] Payload recibido evento={}", payload.get("event"));
        debugWaService.guardarPayload(payload).subscribe();

        try {
            String event = String.valueOf(payload.getOrDefault("event", ""));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) return Mono.just(Map.of("status", "OK"));

            @SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) data.get("Info");
            if (info == null) return Mono.just(Map.of("status", "OK"));

            // ── Receipt: actualizar estado de mensaje enviado ─────────────────
            if ("Receipt".equals(event)) {
                String wamid  = (String) info.get("ID");
                String status = (String) info.get("Status");
                if (wamid != null && status != null) {
                    String estado = mapFactilizaStatus(status);
                    log.debug("[WEBHOOK-WA] Receipt wamid={} -> {}", wamid, estado);
                    return actualizarEstadoMensajeUseCase.ejecutar(wamid, estado, new Date())
                            .thenReturn(Map.<String, Object>of("status", "OK"));
                }
                return Mono.just(Map.of("status", "OK"));
            }

            // ── Message: mensaje entrante del cliente ─────────────────────────
            if ("Message".equals(event)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) data.get("Message");
                if (message != null) {
                    procesarMensajeEntrante(info, message).subscribe(
                            v -> log.debug("[WEBHOOK-WA] Mensaje procesado"),
                            e -> log.warn("[WEBHOOK-WA] Error procesando mensaje: {}", e.getMessage())
                    );
                }
            }

        } catch (Exception e) {
            log.warn("[WEBHOOK-WA] Error procesando webhook: {}", e.getMessage());
        }
        return Mono.just(Map.of("status", "OK"));
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> procesarMensajeEntrante(
            Map<String, Object> info,
            Map<String, Object> message) {

        // Ignorar mensajes enviados por nosotros que rebotan de vuelta
        if (Boolean.TRUE.equals(info.get("FromMe"))) return Mono.empty();

        String fromPhone = (String) info.get("Sender");   // "51957311203@s.whatsapp.net"
        String wamid     = (String) info.get("ID");
        String tipo      = (String) info.getOrDefault("Type",      "text");  // "text" | "media"
        String tsRaw     = info.get("Timestamp") != null ? info.get("Timestamp").toString() : null;
        Date   fecha     = parseFactilizaTimestamp(tsRaw);

        return whatsappService.encontrarContratoIdPorTelefono(fromPhone)
                .flatMap(contratoId -> {
                    if ("media".equals(tipo)) {
                        String base64 = (String) message.get("base64");
                        if (base64 == null || base64.isBlank()) {
                            log.warn("[WEBHOOK-WA] Media sin base64 | contratoId={}", contratoId);
                            return Mono.empty();
                        }
                        Map<String, Object> imgMsg = (Map<String, Object>) message.get("imageMessage");
                        String mime = imgMsg != null
                                ? (String) imgMsg.getOrDefault("mimetype", "image/jpeg")
                                : "image/jpeg";
                        String mediaUrl        = "data:" + mime + ";base64," + base64;
                        String tipoNormalizado = mime.startsWith("image") ? "image" : "document";
                        log.info("[WEBHOOK-WA] Media entrante | contratoId={} mime={} bytes~{}",
                                contratoId, mime, base64.length() * 3 / 4);
                        return whatsappService.registrarMediaEntrante(
                                        contratoId, null, fromPhone, mediaUrl, tipoNormalizado, fecha)
                                .flatMap(mensajeId -> procesarVoucherWhatsappService
                                        .procesar(contratoId, null, null, fromPhone, mediaUrl, tipoNormalizado, mensajeId));
                    } else {
                        String texto = (String) message.getOrDefault("conversation", "");
                        log.info("[WEBHOOK-WA] Texto entrante | contratoId={} from={}", contratoId, fromPhone);
                        return whatsappService.registrarMensajeEntrante(
                                contratoId, null, fromPhone, wamid, texto, fecha)
                                .then(whatsappService.actualizarRespuestaCliente(contratoId));
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[WEBHOOK-WA] No se pudo asociar teléfono {} a contrato: {}", fromPhone, e.getMessage());
                    return Mono.empty();
                });
    }

    /** GET /webhooks/whatsapp — verificación del webhook (challenge) */
    @GetMapping("/webhooks/whatsapp")
    public Mono<String> metaWhatsappVerify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        log.debug("WA webhook verify mode={}", mode);
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

    private Date parseFactilizaTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return new Date();
        try {
            return java.util.Date.from(java.time.OffsetDateTime.parse(ts).toInstant());
        } catch (Exception e) {
            log.warn("[WEBHOOK-WA] No se pudo parsear timestamp: {}", ts);
            return new Date();
        }
    }

    private String mapFactilizaStatus(String status) {
        if (status == null) return "ENVIADO";
        return switch (status.toLowerCase()) {
            case "delivered", "delivery_ack" -> "ENTREGADO";
            case "read"                       -> "LEIDO";
            case "sent"                       -> "ENVIADO";
            case "failed"                     -> "FALLIDO";
            default                           -> "ENVIADO";
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

    public record CerrarPromesaRequest(
            /** CUMPLIDA | INCUMPLIDA | CANCELADA */
            String resultado,
            Double montoPagado,
            String motivo
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
            String observaciones,
            /** ISO YYYY-MM-DD — para pagos retroactivos. Null = usar fecha del OCR o hoy. */
            String fechaPagoReal
    ) {}

    public record PagoManualRequest(
            double monto,
            /** ISO YYYY-MM-DD — fecha real del pago (puede ser retroactiva) */
            String fechaPago,
            /** Número de cuota específica a marcar. Null = aplicar cronológicamente. */
            Integer numeroCuota,
            String observaciones,
            /** GCS path de imagen escaneada — null si no tiene comprobante */
            String imagenPath,
            /** MIGRACION | ADMIN_MANUAL | VOUCHER_FISICO */
            String fuente
    ) {}

    public record RechazarVoucherRequest(
            String motivo,
            String observaciones
    ) {}

    public record EnviarWhatsappRequest(
            String contratoId,
            /** null si se usa mensajeLibre */
            String plantillaId,
            Map<String, String> variablesValores,
            String telefonoDestino,
            /** Texto libre para mensaje personalizado; ignorado si plantillaId está presente */
            String mensajeLibre
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

    // =========================================================================
    // UPLOAD VOUCHER PARA PAGO MANUAL
    // =========================================================================

    /**
     * Sube una imagen/PDF de comprobante a GCS y devuelve el gcsPath.
     * El frontend lo usa para llamar luego a POST /pago-manual con imagenPath.
     *
     * POST /api/v1/cobranzas/casos/{contratoId}/voucher-upload
     * Content-Type: multipart/form-data
     * Body: archivo (FilePart)
     *
     * Response: { "gcsPath": "cobranzas-pagos-manuales/{contratoId}/{uuid}.jpg" }
     */
    @PostMapping(value = "/api/v1/cobranzas/casos/{contratoId}/voucher-upload",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, String>> subirVoucherPagoManual(
            @PathVariable String contratoId,
            @RequestPart("archivo") FilePart archivo) {

        log.info("POST /casos/{}/voucher-upload filename={}", contratoId, archivo.filename());

        return DataBufferUtils.join(archivo.content())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .flatMap(bytes -> {
                    String filename = archivo.filename();
                    String ext = filename != null && filename.contains(".")
                            ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                            : "jpg";
                    String mediaType = "pdf".equals(ext) ? "document" : "image";
                    return mediaStorageService.subirBytes(bytes, mediaType, filename, contratoId);
                })
                .map(result -> Map.of("gcsPath", result.gcsPath()));
    }
}
