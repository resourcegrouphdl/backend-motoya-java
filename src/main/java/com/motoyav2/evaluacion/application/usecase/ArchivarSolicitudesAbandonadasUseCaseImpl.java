package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.port.in.ArchivarSolicitudesAbandonadasUseCase;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivarSolicitudesAbandonadasUseCaseImpl implements ArchivarSolicitudesAbandonadasUseCase {

    private static final String USUARIO_SISTEMA = "sistema-automatico";
    private static final String NOMBRE_SISTEMA  = "Cierre Automático";

    private final SolicitudRepository solicitudRepository;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;
    private final NotificationFacade   notificationFacade;

    @Override
    public Mono<Integer> archivar(int diasInactividad) {
        AtomicInteger contador = new AtomicInteger(0);

        return solicitudRepository.findAbandonadas(diasInactividad)
                .flatMap(solicitud -> {
                    log.info("[CIERRE-AUTO] Archivando solicitud={} estado={} updatedAt={}",
                            solicitud.getId(), solicitud.getEstado(), solicitud.getUpdatedAt());

                    CambiarEstadoCommand cmd = new CambiarEstadoCommand(
                            solicitud.getId(),
                            EstadoSolicitud.ARCHIVADA,
                            USUARIO_SISTEMA,
                            NOMBRE_SISTEMA,
                            "Sin actividad por más de " + diasInactividad + " días"
                    );

                    return cambiarEstadoUseCase.ejecutar(cmd)
                            .doOnSuccess(h -> contador.incrementAndGet())
                            .then(Mono.defer(() -> {
                                if (solicitud.getVendedor() == null) return Mono.empty();
                                String telefonoVendedor = solicitud.getVendedor().getTelefono();
                                String nombreVendedor   = solicitud.getVendedor().getNombre();
                                String codigo = solicitud.getCodigoDeSolicitud() != null
                                        ? solicitud.getCodigoDeSolicitud() : solicitud.getId();
                                return notificationFacade.notificarCambioEstadoVendedor(
                                        solicitud.getId(),
                                        telefonoVendedor,
                                        nombreVendedor != null ? nombreVendedor : "Vendedor",
                                        solicitud.getTitularNombreCompleto() != null
                                                ? solicitud.getTitularNombreCompleto() : "",
                                        codigo,
                                        "Archivada",
                                        "Sin actividad por " + diasInactividad + " días"
                                ).onErrorResume(e -> {
                                    log.warn("[CIERRE-AUTO] Error notificando vendedor solicitud={}: {}", solicitud.getId(), e.getMessage());
                                    return Mono.empty();
                                });
                            }))
                            .onErrorResume(e -> {
                                log.error("[CIERRE-AUTO] Error archivando solicitud={}: {}", solicitud.getId(), e.getMessage());
                                return Mono.empty();
                            });
                }, 4) // concurrency = 4 para no saturar Firestore
                .then(Mono.fromSupplier(contador::get))
                .doOnSuccess(n -> log.info("[CIERRE-AUTO] Procesadas {} solicitudes abandonadas", n));
    }
}
