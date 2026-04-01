package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.motoyav2.notifications.application.service.RecordatoriosPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Endpoint para procesamiento automático de recordatorios de pago.
 *
 * Llamado por Cloud Scheduler (7am Lima = 12pm UTC):
 *   POST /api/v1/notificaciones/procesar-recordatorios
 *   Header: X-Internal-Token: {token}
 *
 * Configuración Cloud Scheduler en GCP:
 *   Job name:     motoya-payment-reminders
 *   Schedule:     0 12 * * *
 *   Time zone:    UTC (equivale a 7am Lima, UTC-5)
 *   Target type:  HTTP
 *   URL:          https://{CLOUD_RUN_URL}/api/v1/notificaciones/procesar-recordatorios
 *   HTTP Method:  POST
 *   Auth:         Add OIDC token (Service Account con roles/run.invoker)
 *   Headers:      X-Internal-Token: {valor de notifications.internal.token}
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notificaciones")
@Tag(name = "Notificaciones - Scheduler", description = "Endpoints para Cloud Scheduler y automatizaciones")
public class RecordatoriosController {

    private final RecordatoriosPagoService recordatoriosService;

    @Value("${notifications.internal.token}")
    private String internalToken;

    @PostMapping("/procesar-recordatorios")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Procesar recordatorios de pago",
            description = "Genera y envía recordatorios de cuotas próximas a vencer y alertas de cuotas vencidas. " +
                    "Invocado diariamente por Cloud Scheduler a las 7am (Lima). " +
                    "Requiere header X-Internal-Token.")
    public Mono<RecordatoriosResponse> procesarRecordatorios(
            @RequestHeader("X-Internal-Token") String token) {

        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }

        log.info("[RECORDATORIOS] Invocación recibida (Cloud Scheduler o manual)");

        return recordatoriosService.procesarRecordatorios()
                .map(result -> new RecordatoriosResponse(
                        result.recordatoriosEnviados(),
                        result.alertasVencidasEnviadas(),
                        result.recordatoriosEnviados() + result.alertasVencidasEnviadas(),
                        "ok"
                ));
    }

    public record RecordatoriosResponse(
            int recordatoriosProximos,
            int alertasVencidas,
            int totalEventosEmitidos,
            String status
    ) {}
}
