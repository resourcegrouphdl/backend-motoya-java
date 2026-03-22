package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import com.motoyav2.evaluacion.application.dto.IngresarSolicitudResult;
import com.motoyav2.evaluacion.domain.port.in.IngresarSolicitudUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.port.out.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngresarSolicitudUseCaseImpl implements IngresarSolicitudUseCase {

    private final ClienteRepository clienteRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ReferenciaRepository referenciaRepository;
    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<IngresarSolicitudResult> ejecutar(IngresarSolicitudCommand cmd) {
        String codigoDeSolicitud = generarCodigo();
        Timestamp ahora = Timestamp.now();

        // 1. Crear o actualizar titular
        Mono<String> titularIdMono = upsertCliente(cmd.titular(), "titular", codigoDeSolicitud, ahora);

        return titularIdMono.flatMap(titularId -> {

            // 2. Crear o actualizar fiador (si existe)
            Mono<String> fiadorIdMono = cmd.fiador() != null
                    ? upsertCliente(cmd.fiador(), "fiador", codigoDeSolicitud, ahora)
                    : Mono.just("");

            return fiadorIdMono.flatMap(fiadorId -> {

                // 3. Crear vehículo
                Map<String, Object> vehiculoMap = buildVehiculoMap(cmd.vehiculo(), codigoDeSolicitud, ahora);
                Mono<String> vehiculoIdMono = vehiculoRepository.create(vehiculoMap);

                return vehiculoIdMono.flatMap(vehiculoId -> {

                    // 4. Crear referencias en paralelo
                    AtomicInteger idx = new AtomicInteger(1);
                    Mono<List<String>> referenciasIdsMono = Flux.fromIterable(cmd.referencias())
                            .flatMap(ref -> {
                                Map<String, Object> refMap = buildReferenciaMap(
                                        ref, titularId, idx.getAndIncrement(), codigoDeSolicitud, ahora);
                                return referenciaRepository.create(refMap);
                            })
                            .collectList();

                    return referenciasIdsMono.flatMap(referenciasIds -> {

                        // 5. Crear solicitud
                        Map<String, Object> solMap = buildSolicitudMap(
                                cmd, titularId,
                                fiadorId.isEmpty() ? null : fiadorId,
                                vehiculoId, referenciasIds,
                                codigoDeSolicitud, ahora);

                        return solicitudRepository.create(solMap)
                                .map(solicitudId -> {
                                    log.info("Solicitud ingresada: id={} codigo={}", solicitudId, codigoDeSolicitud);
                                    return new IngresarSolicitudResult(solicitudId, codigoDeSolicitud, "pendiente");
                                });
                    });
                });
            });
        });
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Mono<String> upsertCliente(IngresarSolicitudCommand.ClienteData data,
                                        String tipo, String codigoDeSolicitud, Timestamp ahora) {
        return clienteRepository.findByDocumentNumber(data.documentNumber())
                .flatMap(existente -> {
                    // cliente ya existe → actualizar y reusar id
                    Map<String, Object> updates = buildClienteMap(data, tipo, codigoDeSolicitud, ahora);
                    updates.put("updatedAt", ahora);
                    return clienteRepository.updateFields(existente.getId(), updates)
                            .thenReturn(existente.getId());
                })
                .switchIfEmpty(
                    // cliente nuevo → crear
                    clienteRepository.create(buildClienteMap(data, tipo, codigoDeSolicitud, ahora))
                );
    }

    private Map<String, Object> buildClienteMap(IngresarSolicitudCommand.ClienteData d,
                                                  String tipo, String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("tipo", tipo);
        m.put("tipoCliente", tipo);
        m.put("codigoDeSolicitud", codigo);
        m.put("documentType", d.documentType());
        m.put("documentNumber", d.documentNumber());
        m.put("nombres", d.nombres());
        m.put("apellidoPaterno", d.apellidoPaterno());
        m.put("apellidoMaterno", d.apellidoMaterno());
        m.put("estadoCivil", d.estadoCivil());
        m.put("email", d.email());
        m.put("fechaNacimiento", d.fechaNacimiento());
        m.put("departamento", d.departamento());
        m.put("provincia", d.provincia());
        m.put("distrito", d.distrito());
        m.put("direccion", d.direccion());
        m.put("ubicacionGPSCasa", d.ubicacionGPSCasa());
        m.put("telefono1", d.telefono1());
        m.put("telefono2", d.telefono2());
        m.put("ocupacion", d.ocupacion());
        m.put("rangoIngresos", d.rangoIngresos());
        m.put("tipoVivienda", d.tipoVivienda());
        m.put("licenciaConducir", d.licenciaConducir());
        m.put("numeroLicencia", d.numeroLicencia());
        if (d.archivos() != null && !d.archivos().isEmpty()) {
            m.put("archivos", d.archivos());
        }
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private Map<String, Object> buildVehiculoMap(IngresarSolicitudCommand.VehiculoData d,
                                                   String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("marca", d.marca());
        m.put("modelo", d.modelo());
        m.put("color", d.color());
        m.put("anio", d.anio());
        m.put("codigoDeSolicitud", codigo);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private Map<String, Object> buildReferenciaMap(IngresarSolicitudCommand.ReferenciaData d,
                                                     String titularId, int numero,
                                                     String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", d.nombre());
        m.put("apellidos", d.apellidos());
        m.put("telefono", d.telefono());
        m.put("parentesco", d.parentesco());
        m.put("titularId", titularId);
        m.put("numero", numero);
        m.put("codigoDeSolicitud", codigo);
        m.put("estadoVerificacion", "pendiente");
        m.put("rechazada", false);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private Map<String, Object> buildSolicitudMap(IngresarSolicitudCommand cmd,
                                                   String titularId, String fiadorId,
                                                   String vehiculoId, List<String> referenciasIds,
                                                   String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("codigoDeSolicitud", codigo);
        m.put("estado", "pendiente");
        m.put("prioridad", "Media");
        m.put("titularId", titularId);
        // Desnormalización para listados rápidos
        String nombreCompleto = (cmd.titular().nombres() + " "
                + cmd.titular().apellidoPaterno() + " "
                + cmd.titular().apellidoMaterno()).trim();
        m.put("titularNombreCompleto", nombreCompleto);
        m.put("titularDni", cmd.titular().documentNumber());
        m.put("titularTelefono", cmd.titular().telefono1());
        m.put("titularEmail", cmd.titular().email());
        m.put("fiadorId", fiadorId);
        m.put("vehiculoId", vehiculoId);
        m.put("referenciasIds", referenciasIds);

        // Financiero legacy
        IngresarSolicitudCommand.FinanciamientoData fin = cmd.financiamiento();
        m.put("precioCompraMoto", fin.precioCompraMoto());
        m.put("inicial", fin.inicial());
        m.put("plazoQuincenas", fin.plazoQuincenas());
        m.put("montoCuota", fin.montoCuota());

        // Vendedor
        IngresarSolicitudCommand.VendedorData v = cmd.vendedor();
        m.put("vendedorId", v.id());
        m.put("vendedorNombre", v.nombre());
        Map<String, Object> vendedorMap = new HashMap<>();
        vendedorMap.put("id", v.id());
        vendedorMap.put("nombre", v.nombre());
        vendedorMap.put("tienda", v.tienda());
        m.put("vendedor", vendedorMap);
        m.put("mensajeOpcional", cmd.mensajeOpcional());

        m.put("certificadoGenerado", false);
        m.put("contratoGenerado", false);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }

    private String generarCodigo() {
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String alfa = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(alfa.charAt((int) (Math.random() * alfa.length())));
        }
        return "MDCR-" + fecha + "-" + sb;
    }
}
