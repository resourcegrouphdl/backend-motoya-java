package com.motoyav2.alertascenter.application.service;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.alertascenter.domain.model.AlertaDatosEnriquecidos;
import com.motoyav2.alertascenter.domain.model.SubTipoAlerta;
import com.motoyav2.alertascenter.domain.model.TipoAlerta;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnriquecedorAlertaService {

    private final Firestore firestore;

    @Value("${alertas.collections.solicitudes:solicitudes_dev}")
    private String solicitudesCollection;

    @Value("${alertas.collections.clientes:clientes_v1_dev}")
    private String clientesCollection;

    @Value("${alertas.collections.tiendas:tienda_profiles}")
    private String tiendasCollection;

    @Value("${alertas.collections.contratos:contratos}")
    private String contratosCollection;

    public Mono<AlertaDatosEnriquecidos> enriquecer(TipoAlerta tipo, SubTipoAlerta subTipo, String fuenteId) {
        return switch (tipo) {
            case NUEVA_SOLICITUD -> enriquecerSolicitud(fuenteId);
            case CONTRATO_ACTUALIZADO -> enriquecerContrato(fuenteId, subTipo);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NUEVA SOLICITUD
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<AlertaDatosEnriquecidos> enriquecerSolicitud(String solicitudId) {
        return FirestoreUtils.toMono(firestore.collection(solicitudesCollection).document(solicitudId).get())
                .flatMap(snap -> {
                    if (!snap.exists()) {
                        return Mono.error(new NotFoundException("Solicitud no encontrada: " + solicitudId));
                    }

                    Map<String, Object> data = snap.getData() != null ? snap.getData() : new HashMap<>();
                    String titularId = (String) data.get("titularId");
                    String codigoDeSolicitud = (String) data.getOrDefault("codigoDeSolicitud", solicitudId);
                    String tiendaId = extractTiendaId(data);

                    log.debug("Enriqueciendo solicitud {} — titularId={}, tiendaId={}", solicitudId, titularId, tiendaId);

                    Mono<String> titularNombreMono = titularId != null
                            ? fetchTitularNombre(titularId)
                            : Mono.just("Cliente");

                    Mono<String> tiendaNombreMono = tiendaId != null
                            ? fetchTiendaNombre(tiendaId)
                            : Mono.just("Tienda");

                    return Mono.zip(titularNombreMono, tiendaNombreMono)
                            .map(tuple -> {
                                String titularNombre = tuple.getT1();
                                String nombreTienda = tuple.getT2();

                                Map<String, Object> payload = new HashMap<>();
                                payload.put("solicitudId", solicitudId);
                                payload.put("codigoDeSolicitud", codigoDeSolicitud);
                                payload.put("titularNombre", titularNombre);
                                payload.put("nombreTienda", nombreTienda);

                                String titulo = "Nueva solicitud de crédito";
                                String mensaje = String.format("Solicitud %s de %s — %s",
                                        codigoDeSolicitud, titularNombre, nombreTienda);

                                return new AlertaDatosEnriquecidos(titulo, mensaje, payload, solicitudesCollection);
                            });
                });
    }

    @SuppressWarnings("unchecked")
    private String extractTiendaId(Map<String, Object> data) {
        Object raw = data.get("vendedorTienda");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.get(0).toString();
        }
        if (data.get("vendedor") instanceof Map<?, ?> vendedor) {
            Object tienda = vendedor.get("tienda");
            if (tienda != null && !tienda.toString().isBlank()) {
                return tienda.toString();
            }
        }
        log.warn("No se encontró tiendaId en el documento de solicitud. Campos: {}", data.keySet());
        return null;
    }

    private Mono<String> fetchTitularNombre(String titularId) {
        return FirestoreUtils.toMono(firestore.collection(clientesCollection).document(titularId).get())
                .map(snap -> {
                    if (!snap.exists()) return "Cliente";
                    Map<String, Object> d = snap.getData() != null ? snap.getData() : new HashMap<>();
                    String nombres = (String) d.getOrDefault("nombres", "");
                    String apPat = (String) d.getOrDefault("apellidoPaterno", "");
                    String apMat = (String) d.getOrDefault("apellidoMaterno", "");
                    return String.format("%s %s %s", nombres, apPat, apMat).trim().replaceAll("\\s+", " ");
                })
                .onErrorResume(e -> {
                    log.warn("No se pudo obtener nombre del titular {}: {}", titularId, e.getMessage());
                    return Mono.just("Cliente");
                });
    }

    private Mono<String> fetchTiendaNombre(String tiendaId) {
        return FirestoreUtils.toMono(firestore.collection(tiendasCollection).document(tiendaId).get())
                .map(snap -> {
                    if (!snap.exists()) return "Tienda";
                    Map<String, Object> d = snap.getData() != null ? snap.getData() : new HashMap<>();
                    return (String) d.getOrDefault("businessName", "Tienda");
                })
                .onErrorResume(e -> {
                    log.warn("No se pudo obtener nombre de tienda {}: {}", tiendaId, e.getMessage());
                    return Mono.just("Tienda");
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONTRATO ACTUALIZADO
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<AlertaDatosEnriquecidos> enriquecerContrato(String contratoId, SubTipoAlerta subTipo) {
        return FirestoreUtils.toMono(firestore.collection(contratosCollection).document(contratoId).get())
                .flatMap(snap -> {
                    if (!snap.exists()) {
                        return Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId));
                    }

                    Map<String, Object> data = snap.getData() != null ? snap.getData() : new HashMap<>();
                    String numeroContrato = (String) data.getOrDefault("numeroContrato", contratoId);
                    String titularNombre = extractTitularNombreDeContrato(data);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("contratoId", contratoId);
                    payload.put("numeroContrato", numeroContrato);
                    payload.put("titularNombre", titularNombre);
                    payload.put("tipoActualizacion", subTipo != null ? subTipo.name() : "ACTUALIZADO");

                    String titulo = subTipo == SubTipoAlerta.BOUCHER_SUBIDO
                            ? "Boucher de pago inicial subido"
                            : "Factura de vehículo actualizada";

                    String mensaje = String.format("Contrato %s (%s) — %s requiere revisión",
                            numeroContrato, titularNombre, titulo.toLowerCase());

                    return Mono.just(new AlertaDatosEnriquecidos(titulo, mensaje, payload, contratosCollection));
                });
    }

    @SuppressWarnings("unchecked")
    private String extractTitularNombreDeContrato(Map<String, Object> data) {
        if (data.get("titular") instanceof Map<?, ?> titular) {
            Object nombre = titular.get("nombreCompleto");
            if (nombre != null) return nombre.toString();
            Map<String, Object> t = (Map<String, Object>) titular;
            String nombres = (String) t.getOrDefault("nombres", "");
            String apPat = (String) t.getOrDefault("apellidoPaterno", "");
            return String.format("%s %s", nombres, apPat).trim();
        }
        return "Titular";
    }
}
