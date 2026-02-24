package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.dto.AlertasResumenDto;
import com.motoyav2.cobranza.application.dto.DashboardDto;
import com.motoyav2.cobranza.application.port.out.AlertaCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MetricasPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MetricasDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.MetricasAgenteDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MetricasPort metricasPort;
    private final AlertaCobranzaPort alertaPort;

    // -------------------------------------------------------------------------
    // Dashboard KPIs
    // -------------------------------------------------------------------------

    public Mono<DashboardDto> getDashboard(String storeId, String userId, String rol) {
        return metricasPort.findResumenActual()
                .map(doc -> buildDashboard(doc, userId, rol))
                .switchIfEmpty(Mono.just(emptyDashboard()));
    }

    private DashboardDto buildDashboard(MetricasDocument doc, String userId, String rol) {
        if ("AGENTE".equalsIgnoreCase(rol) && doc.getAgentes() != null) {
            Map<String, MetricasAgenteDocument> agentes = doc.getAgentes();
            MetricasAgenteDocument agente = agentes.get(userId);

            if (agente != null) {
                return new DashboardDto(
                        agente.getPromesasHoy(),
                        doc.getPromesasIncumplidas(),
                        doc.getVouchersPendientes(),
                        doc.getCasosCriticos(),
                        null,
                        agente.getRecuperacionMes(),
                        doc.getPorcentajeAutomatizado(),
                        null,
                        agente.getCasosAsignados(),
                        doc.getUltimaActualizacion() != null
                                ? doc.getUltimaActualizacion().toInstant().toString()
                                : null
                );
            }
        }

        // Global (SUPERVISOR / ADMIN) o fallback cuando no hay datos por agente
        return new DashboardDto(
                doc.getPromesasVencenHoy(),
                doc.getPromesasIncumplidas(),
                doc.getVouchersPendientes(),
                doc.getCasosCriticos(),
                doc.getMoraTotal(),
                doc.getRecuperacionMes(),
                doc.getPorcentajeAutomatizado(),
                doc.getTasaRecuperacion(),
                doc.getCasosActivos(),
                doc.getUltimaActualizacion() != null
                        ? doc.getUltimaActualizacion().toInstant().toString()
                        : null
        );
    }

    private DashboardDto emptyDashboard() {
        return new DashboardDto(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0, null);
    }

    // -------------------------------------------------------------------------
    // Alertas Resumen
    // -------------------------------------------------------------------------

    public Mono<AlertasResumenDto> getAlertasResumen(String storeId, String agenteId, String rol) {
        return alertaPort.findByStoreId(storeId)
                .collectList()
                .map(alertas -> {
                    long criticas = alertas.stream()
                            .filter(a -> "CRITICAL".equalsIgnoreCase(a.getNivel()))
                            .count();
                    long warnings = alertas.stream()
                            .filter(a -> "WARNING".equalsIgnoreCase(a.getNivel()))
                            .count();
                    long infos = alertas.stream()
                            .filter(a -> "INFO".equalsIgnoreCase(a.getNivel()))
                            .count();
                    long noLeidas = alertas.stream()
                            .filter(a -> Boolean.FALSE.equals(a.getLeida()))
                            .count();
                    return new AlertasResumenDto(criticas, warnings, infos, noLeidas);
                });
    }
}
