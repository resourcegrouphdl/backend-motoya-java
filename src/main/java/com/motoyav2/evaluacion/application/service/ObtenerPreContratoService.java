package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.port.in.ObtenerPreContratoUseCase;
import com.motoyav2.evaluacion.application.port.out.ClientePort;
import com.motoyav2.evaluacion.application.port.out.TiendaPort;
import com.motoyav2.evaluacion.application.port.out.VehiculoPort;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.TiendaInfo;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse.FinancierosPreDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse.FiadorPreDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse.TiendaPreDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse.TitularPreDto;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseSolicitud;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseSolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Lee el expediente aprobado y mapea los datos al formato de CrearContratoManualRequest.
 *
 * Mapeos clave:
 *  - apellidos      → apellidoPaterno + " " + apellidoMaterno
 *  - numeroCuotas   → plazoQuincenas (safe parse, String o Long)
 *  - cuotaMensual   → montoCuota (es el monto quincenal — nombre confuso en módulo contrato)
 *  - montoFinanciado → precioCompraMoto - inicial
 *  - tasaInteresAnual → datosFinancieros.tasaInteresAnual o ZERO
 *  - tiendaId       → vendedor.tienda[0] o vendedorTienda[0]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObtenerPreContratoService implements ObtenerPreContratoUseCase {

    private final FirebaseSolicitudRepository solicitudRepository;
    private final ClientePort clientePort;
    private final VehiculoPort vehiculoPort;
    private final TiendaPort tiendaPort;

    // Estados que permiten crear contrato
    private static final List<String> ESTADOS_APTOS = List.of(
            "aprobado", "pendiente_contrato", "en_contrato",
            "contrato_generado", "contrato_firmado"
    );

    @Override
    public Mono<PreContratoResponse> ejecutar(String solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(this::construirPreContrato);
    }

    private Mono<PreContratoResponse> construirPreContrato(FirebaseSolicitud sol) {
        String titularId  = sol.getTitularId();
        String fiadorId   = sol.getFiadorId();
        String vehiculoId = sol.getVehiculoId();
        String tiendaId   = resolverTiendaId(sol);

        Mono<Persona> titularMono = clientePort.buscarPorId(titularId);

        Mono<Persona> fiadorMono = fiadorId != null && !fiadorId.isBlank()
                ? clientePort.buscarPorId(fiadorId).defaultIfEmpty(Persona.builder().build())
                : Mono.just(Persona.builder().build());

        Mono<Vehiculo> vehiculoMono = vehiculoId != null
                ? vehiculoPort.buscarPorId(vehiculoId).defaultIfEmpty(Vehiculo.builder().build())
                : Mono.just(Vehiculo.builder().build());

        Mono<TiendaInfo> tiendaMono = tiendaId != null
                ? tiendaPort.obtenerInfoTienda(tiendaId).defaultIfEmpty(new TiendaInfo(null, null))
                : Mono.just(new TiendaInfo(null, null));

        return Mono.zip(titularMono, fiadorMono, vehiculoMono, tiendaMono)
                .map(t -> {
                    Persona titular    = t.getT1();
                    Persona fiadorRaw  = t.getT2();
                    Persona fiador     = fiadorRaw.getId() != null ? fiadorRaw : null;
                    Vehiculo vehiculo  = t.getT3();
                    TiendaInfo tienda  = t.getT4();

                    boolean puedeCrear = sol.getEstado() != null
                            && ESTADOS_APTOS.contains(sol.getEstado().toLowerCase());

                    String advertencia = construirAdvertencia(sol, titular, vehiculo, puedeCrear);

                    log.info("Pre-contrato generado — solicitud: {}, estado: {}, puedeCrear: {}",
                            sol.getCodigoDeSolicitud(), sol.getEstado(), puedeCrear);

                    return PreContratoResponse.builder()
                            .estadoSolicitud(sol.getEstado())
                            .numeroSolicitud(resolverNumeroSolicitud(sol))
                            .puedeCrearContrato(puedeCrear)
                            .advertencia(advertencia)
                            .titular(mapTitular(titular))
                            .fiador(fiador != null ? mapFiador(fiador) : null)
                            .tienda(mapTienda(tienda, tiendaId))
                            .datosFinancieros(mapFinancieros(sol, vehiculo))
                            .evaluacionId(solicitudId(sol))
                            .build();
                });
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private TitularPreDto mapTitular(Persona p) {
        if (p == null) return null;
        return TitularPreDto.builder()
                .nombres(p.getNombres())
                .apellidos(combinarApellidos(p.getApellidoPaterno(), p.getApellidoMaterno()))
                .tipoDocumento(p.getTipoDeDocumento())
                .numeroDocumento(p.getNumeroDeDocumento())
                .telefono(p.getTelefono1())
                .email(p.getEmail())
                .direccion(p.getDireccion())
                .distrito(p.getDistrito())
                .provincia(p.getProvincia())
                .departamento(p.getDepartamento())
                .build();
    }

    private FiadorPreDto mapFiador(Persona p) {
        if (p == null) return null;
        return FiadorPreDto.builder()
                .nombres(p.getNombres())
                .apellidos(combinarApellidos(p.getApellidoPaterno(), p.getApellidoMaterno()))
                .tipoDocumento(p.getTipoDeDocumento())
                .numeroDocumento(p.getNumeroDeDocumento())
                .telefono(p.getTelefono1())
                .email(p.getEmail())
                .direccion(p.getDireccion())
                .distrito(p.getDistrito())
                .provincia(p.getProvincia())
                .departamento(p.getDepartamento())
                .parentesco(null)  // no está en FirebaseCliente — frontend puede agregar
                .build();
    }

    private TiendaPreDto mapTienda(TiendaInfo t, String tiendaId) {
        if (t == null) return TiendaPreDto.builder().tiendaId(tiendaId).build();
        return TiendaPreDto.builder()
                .tiendaId(t.tiendaId() != null ? t.tiendaId() : tiendaId)
                .nombreTienda(t.nombre())
                .direccion(t.direccion())
                .ciudad(t.ciudad())
                .build();
    }

    private FinancierosPreDto mapFinancieros(FirebaseSolicitud sol, Vehiculo vehiculo) {
        BigDecimal precio  = toLong(sol.getPrecioCompraMoto());
        BigDecimal inicial = toLong(sol.getInicial());
        BigDecimal monto   = precio != null && inicial != null
                ? precio.subtract(inicial)
                : null;

        // Intentar obtener tasa de datosFinancieros si existe
        BigDecimal tasa = resolverTasaInteres(sol.getDatosFinancieros());

        BigDecimal cuota = sol.getMontoCuota() != null
                ? BigDecimal.valueOf(sol.getMontoCuota())
                : null;

        return FinancierosPreDto.builder()
                .precioVehiculo(precio)
                .cuotaInicial(inicial)
                .montoFinanciado(monto)
                .tasaInteresAnual(tasa)
                .numeroCuotas(parsearPlazo(sol.getPlazoQuincenas()))
                .cuotaMensual(cuota)
                .marcaVehiculo(vehiculo != null ? vehiculo.getMarca() : null)
                .modeloVehiculo(vehiculo != null ? vehiculo.getModelo() : null)
                .anioVehiculo(vehiculo != null ? vehiculo.getAnio() : null)
                .colorVehiculo(vehiculo != null ? vehiculo.getColor() : null)
                .numeroMotor(null)
                .numeroChasis(null)
                .placa(null)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String combinarApellidos(String paterno, String materno) {
        if (paterno == null && materno == null) return null;
        if (paterno == null) return materno;
        if (materno == null) return paterno;
        return (paterno.trim() + " " + materno.trim()).trim();
    }

    /** Extrae tiendaId desde vendedor.tienda[0] o vendedorTienda[0] */
    private String resolverTiendaId(FirebaseSolicitud sol) {
        if (sol.getVendedor() != null
                && sol.getVendedor().getTienda() != null
                && !sol.getVendedor().getTienda().isEmpty()) {
            return sol.getVendedor().getTienda().getFirst();
        }
        if (sol.getVendedorTienda() != null && !sol.getVendedorTienda().isEmpty()) {
            return sol.getVendedorTienda().getFirst();
        }
        return null;
    }

    /** plazoQuincenas puede ser String "16" o Long 16 en Firestore */
    private Integer parsearPlazo(Object plazo) {
        if (plazo == null) return null;
        if (plazo instanceof Long l)   return l.intValue();
        if (plazo instanceof Integer i) return i;
        if (plazo instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private BigDecimal toLong(Long value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal resolverTasaInteres(Map<String, Object> datosFinancieros) {
        if (datosFinancieros == null) return BigDecimal.ZERO;
        Object tasa = datosFinancieros.get("tasaInteresAnual");
        if (tasa == null) tasa = datosFinancieros.get("tasa");
        if (tasa == null) return BigDecimal.ZERO;
        if (tasa instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (tasa instanceof String s) {
            try { return new BigDecimal(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return BigDecimal.ZERO;
    }

    private String resolverNumeroSolicitud(FirebaseSolicitud sol) {
        if (sol.getNumeroSolicitud() != null) return sol.getNumeroSolicitud();
        return sol.getCodigoDeSolicitud();
    }

    private String solicitudId(FirebaseSolicitud sol) {
        return sol.getFormularioId();
    }

    private String construirAdvertencia(FirebaseSolicitud sol, Persona titular,
                                        Vehiculo vehiculo, boolean puedeCrear) {
        if (!puedeCrear) {
            return "La solicitud está en estado '" + sol.getEstado()
                    + "'. Solo se puede crear contrato para solicitudes aprobadas.";
        }
        StringBuilder sb = new StringBuilder();
        if (titular == null || titular.getId() == null) sb.append("Datos del titular no encontrados. ");
        if (vehiculo == null || vehiculo.getId() == null) sb.append("Datos del vehículo no encontrados. ");
        if (sol.getPrecioCompraMoto() == null) sb.append("Precio del vehículo no disponible. ");
        if (sol.getMontoCuota() == null) sb.append("Monto de cuota no disponible. ");
        if (sol.getPlazoQuincenas() == null) sb.append("Plazo de quincenas no disponible. ");
        return sb.length() > 0 ? sb.toString().trim() : null;
    }
}
