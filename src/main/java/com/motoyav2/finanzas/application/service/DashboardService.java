package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.ObtenerDashboardUseCase;
import com.motoyav2.finanzas.application.port.out.AlertaFinancieraPort;
import com.motoyav2.finanzas.application.port.out.KpisPort;
import com.motoyav2.finanzas.domain.model.AlertaFinanciera;
import com.motoyav2.finanzas.domain.model.DashboardFinanzas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("finanzasDashboardService")
@RequiredArgsConstructor
public class DashboardService implements ObtenerDashboardUseCase {

    private final KpisPort kpisPort;
    private final AlertaFinancieraPort alertaPort;

    @Override
    public Mono<DashboardFinanzas> ejecutar() {
        Mono<Map<String, Object>> kpisMono = kpisPort.obtenerKpis();
        Mono<List<DashboardFinanzas.ProximoPago>> proximosMono = alertaPort.findProximosPagos();
        Mono<List<AlertaFinanciera>> alertasMono = alertaPort.findAllAlertas().collectList();

        return Mono.zip(kpisMono, proximosMono, alertasMono)
                .map(tuple -> {
                    Map<String, Object> kpis = tuple.getT1();
                    return DashboardFinanzas.builder()
                            .totalFacturasPendientes(toLong(kpis.get("totalFacturasPendientes")))
                            .montoFacturasPendientes(toBigDecimal(kpis.get("montoFacturasPendientes")))
                            .pagosTiendaHoy(toLong(kpis.get("pagosTiendaHoy")))
                            .pagosVencidos(toLong(kpis.get("pagosVencidos")))
                            .egresosDelMes(toBigDecimal(kpis.get("egresosDelMes")))
                            .comisionesPendientes(toLong(kpis.get("comisionesPendientes")))
                            .proximosPagos(tuple.getT2())
                            .alertas(tuple.getT3())
                            .build();
                });
    }

    private long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
