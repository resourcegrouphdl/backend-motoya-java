package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.contabilidad.domain.model.PuntoRecaudacion;
import com.motoyav2.contabilidad.domain.port.out.CronogramaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CronogramaFirestoreAdapter implements CronogramaPort {

    private static final String COL = "cobranzas-casos";
    private static final DateTimeFormatter FMT_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Firestore firestore;

    @Override
    public Flux<PuntoRecaudacion> proyectarFlujo(int meses, String tiendaId) {
        LocalDate hoy   = LocalDate.now();
        LocalDate limite = hoy.plusMonths(meses);

        Query query = firestore.collection(COL)
                .whereEqualTo("cicloVida", "ACTIVO");

        if (tiendaId != null && !tiendaId.isBlank()) {
            query = query.whereEqualTo("storeId", tiendaId);
        }

        return toMono(query.get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .collectList()
                .flatMapMany(docs -> {
                    // Agrupar cuotas pendientes/vigentes por mes
                    Map<String, Double>  montosPorMes  = new TreeMap<>();
                    Map<String, Integer> conteoPorMes  = new TreeMap<>();

                    // Pre-llenar meses del período para garantizar orden
                    LocalDate cursor = hoy.withDayOfMonth(1);
                    while (!cursor.isAfter(limite.withDayOfMonth(1))) {
                        String clave = cursor.format(FMT_MES);
                        montosPorMes.put(clave, 0.0);
                        conteoPorMes.put(clave, 0);
                        cursor = cursor.plusMonths(1);
                    }

                    for (var doc : docs) {
                        Object cronogramaObj = doc.get("cronograma");
                        if (!(cronogramaObj instanceof List<?> cronograma)) continue;

                        for (Object itemObj : cronograma) {
                            if (!(itemObj instanceof Map<?, ?> cuota)) continue;

                            Object estadoObj = cuota.get("estado");
                            if (estadoObj == null) continue;
                            String estado = estadoObj.toString();
                            if (!"PENDIENTE".equalsIgnoreCase(estado) && !"VIGENTE".equalsIgnoreCase(estado)) continue;

                            Object fechaVencObj = cuota.get("fechaVencimiento");
                            if (fechaVencObj == null) continue;
                            LocalDate fechaVenc;
                            try {
                                fechaVenc = LocalDate.parse(fechaVencObj.toString().substring(0, 10));
                            } catch (Exception e) {
                                continue;
                            }

                            if (fechaVenc.isBefore(hoy) || fechaVenc.isAfter(limite)) continue;

                            Object montoObj = cuota.get("monto");
                            double monto = montoObj instanceof Number n ? n.doubleValue() : 0.0;

                            String clave = fechaVenc.format(FMT_MES);
                            montosPorMes.merge(clave, monto, Double::sum);
                            conteoPorMes.merge(clave, 1, Integer::sum);
                        }
                    }

                    return Flux.fromIterable(montosPorMes.entrySet())
                            .map(entry -> PuntoRecaudacion.builder()
                                    .etiqueta(entry.getKey())
                                    .cantidadPagos(conteoPorMes.getOrDefault(entry.getKey(), 0))
                                    .montoTotal(entry.getValue())
                                    .build());
                })
                .onErrorResume(e -> {
                    log.error("Error proyectando flujo de caja: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
