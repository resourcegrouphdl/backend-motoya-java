package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.out.HistorialEstadoRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.port.out.UsuarioRepository;
import com.motoyav2.evaluacion.domain.service.EstadoSolicitudStateMachine;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CambiarEstadoUseCaseImpl implements CambiarEstadoUseCase {

    /** Estados que generan notificación WA al vendedor. */
    private static final Set<EstadoSolicitud> ESTADOS_NOTIFICAR_VENDEDOR = Set.of(
            EstadoSolicitud.APROBADO,
            EstadoSolicitud.RECHAZADO,
            EstadoSolicitud.CONDICIONAL,
            EstadoSolicitud.CERTIFICADO_GENERADO,
            EstadoSolicitud.DOCUMENTOS_OBSERVADOS,
            EstadoSolicitud.ARCHIVADA
    );

    private static final Map<EstadoSolicitud, String> LABELS_ESTADO = Map.of(
            EstadoSolicitud.APROBADO,              "✅ Aprobado",
            EstadoSolicitud.RECHAZADO,             "❌ Rechazado",
            EstadoSolicitud.CONDICIONAL,           "⚠️ Condicional",
            EstadoSolicitud.CERTIFICADO_GENERADO,  "📄 Certificado Generado",
            EstadoSolicitud.DOCUMENTOS_OBSERVADOS, "📋 Documentos Observados",
            EstadoSolicitud.ARCHIVADA,             "📦 Archivada"
    );

    /** Estados donde el motivo se persiste como motivoRechazo en el documento principal */
    private static final Set<EstadoSolicitud> ESTADOS_RECHAZO = Set.of(
            EstadoSolicitud.RECHAZADO,
            EstadoSolicitud.CANCELADO,
            EstadoSolicitud.CLIENTE_RECHAZADO,
            EstadoSolicitud.FIADOR_RECHAZADO,
            EstadoSolicitud.REFERENCIAS_RECHAZADAS,
            EstadoSolicitud.VEHICULO_RECHAZADO,
            EstadoSolicitud.DATOS_NO_VERIFICADOS,
            EstadoSolicitud.DOCUMENTOS_OBSERVADOS,
            EstadoSolicitud.DOCUMENTOS_INCOMPLETOS
    );

    private final SolicitudRepository      solicitudRepository;
    private final HistorialEstadoRepository historialEstadoRepository;
    private final UsuarioRepository         usuarioRepository;
    private final NotificationFacade        notificationFacade;

    @Override
    public Mono<HistorialEstado> ejecutar(CambiarEstadoCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {
                    EstadoSolicitudStateMachine.validar(solicitud.getEstado(), command.nuevoEstado());

                    Timestamp ahora = Timestamp.now();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado", command.nuevoEstado().getFirestoreValue());
                    updates.put("updatedAt", ahora);

                    // Persistir motivoRechazo en el documento cuando la transición es a un estado de rechazo
                    if (ESTADOS_RECHAZO.contains(command.nuevoEstado())
                            && command.motivo() != null && !command.motivo().isBlank()) {
                        updates.put("motivoRechazo", command.motivo());
                    }

                    HistorialEstado historial = HistorialEstado.builder()
                            .solicitudId(command.solicitudId())
                            .estadoAnterior(solicitud.getEstado())
                            .estadoNuevo(command.nuevoEstado())
                            .fechaCambio(ahora)
                            .usuarioId(command.usuarioId())
                            .usuarioNombre(command.usuarioNombre())
                            .motivo(command.motivo())
                            .build();

                    return solicitudRepository.updateFields(command.solicitudId(), updates)
                            .then(historialEstadoRepository.save(historial))
                            .doOnSuccess(h -> notificarVendedorSiAplica(solicitud, command))
                            .thenReturn(historial);
                });
    }

    private void notificarVendedorSiAplica(Solicitud solicitud, CambiarEstadoCommand command) {
        if (!ESTADOS_NOTIFICAR_VENDEDOR.contains(command.nuevoEstado())) return;
        if (solicitud.getVendedorId() == null) return;

        String label = LABELS_ESTADO.getOrDefault(command.nuevoEstado(),
                command.nuevoEstado().getFirestoreValue());
        String codigo = solicitud.getCodigoDeSolicitud() != null
                ? solicitud.getCodigoDeSolicitud() : solicitud.getId();

        usuarioRepository.findById(solicitud.getVendedorId())
                .flatMap(vendedor -> notificationFacade.notificarCambioEstadoVendedor(
                        solicitud.getId(),
                        vendedor.getTelefono(),
                        vendedor.getNombre(),
                        solicitud.getTitularNombreCompleto() != null ? solicitud.getTitularNombreCompleto() : "",
                        codigo,
                        label,
                        command.motivo()
                ))
                .onErrorResume(e -> {
                    log.warn("[CAMBIO-ESTADO] Error notificando vendedor solicitud={}: {}", solicitud.getId(), e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }
}
