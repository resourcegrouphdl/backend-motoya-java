package com.motoyav2.evaluacion.application.assembler;

import com.motoyav2.evaluacion.application.port.out.ClientePort;
import com.motoyav2.evaluacion.application.port.out.ReferenciasPort;
import com.motoyav2.evaluacion.application.port.out.TiendaPort;
import com.motoyav2.evaluacion.application.port.out.VehiculoPort;
import com.motoyav2.evaluacion.application.port.out.VendedorPort;
import com.motoyav2.evaluacion.domain.model.Evaluacion;
import com.motoyav2.evaluacion.domain.model.ExpedienteDeEvaluacion;
import com.motoyav2.evaluacion.domain.model.ExpedienteSeed;
import com.motoyav2.evaluacion.domain.model.Financiamiento;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.TiendaInfo;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.model.VendedorInfo;
import com.motoyav2.evaluacion.domain.policy.FinanciamientoInicialPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpedienteAssembler {

    private final ClientePort clientePort;
    private final VehiculoPort vehiculoPort;
    private final ReferenciasPort referenciasPort;
    private final VendedorPort vendedorPort;
    private final TiendaPort tiendaPort;

    public Mono<ExpedienteDeEvaluacion> ensamblar(ExpedienteSeed seed) {
        Evaluacion evaluacionBase = seed.evaluacion();

        Mono<Persona> titularMono = clientePort.buscarPorId(evaluacionBase.getTitularId());

        Mono<Persona> fiadorMono = evaluacionBase.tieneFiador()
                ? clientePort.buscarPorId(evaluacionBase.getFiadorId())
                : Mono.empty();

        Mono<Vehiculo> vehiculoMono = vehiculoPort.buscarPorId(evaluacionBase.getVehiculoId());

        Mono<List<ReferenciasDelTitular>> referenciasMono = referenciasPort
                .buscarPorIds(seed.referenciasIds())
                .collectList();

        Mono<VendedorInfo> vendedorMono = resolverVendedor(evaluacionBase);
        Mono<TiendaInfo> tiendaMono = resolverTienda(evaluacionBase);

        return Mono.zip(
                titularMono,
                fiadorMono.defaultIfEmpty(Persona.builder().build()),
                vehiculoMono,
                referenciasMono,
                vendedorMono,
                tiendaMono
        ).map(tuple -> {
            Persona titular = tuple.getT1();
            Persona fiadorRaw = tuple.getT2();
            Persona fiador = fiadorRaw.getId() != null ? fiadorRaw : null;
            Vehiculo vehiculo = tuple.getT3();
            List<ReferenciasDelTitular> referencias = tuple.getT4();
            VendedorInfo vendedorInfo = tuple.getT5();
            TiendaInfo tiendaInfo = tuple.getT6();

            Evaluacion evaluacionEnriquecida = evaluacionBase
                    .enriquecerConContextoComercial(vendedorInfo, tiendaInfo);

            Financiamiento financiamiento = FinanciamientoInicialPolicy.calcular(
                    evaluacionEnriquecida, vehiculo,
                    seed.montoCuota(), seed.plazoQuincenas(), seed.precioCompraMoto());

            log.info("Expediente ensamblado - titular: {}, fiador: {}, vehiculo: {}, referencias: {}, docsTitular: {}, docsFiador: {}",
                    titular.getNombreCompleto(),
                    fiador != null ? fiador.getNombreCompleto() : "SIN FIADOR",
                    vehiculo.getDescripcionCompleta(),
                    referencias.size(),
                    titular.getDocumentos() != null ? titular.getDocumentos().size() : 0,
                    fiador != null && fiador.getDocumentos() != null ? fiador.getDocumentos().size() : 0);

            return ExpedienteDeEvaluacion.crear(
                    evaluacionEnriquecida, titular, fiador, vehiculo, financiamiento, referencias);
        });
    }

    private Mono<VendedorInfo> resolverVendedor(Evaluacion evaluacion) {
        VendedorInfo fallback = new VendedorInfo(evaluacion.getVendedorNombre(), null, null);
        if (evaluacion.getVendedorId() == null) {
            return Mono.just(fallback);
        }
        return vendedorPort.obtenerInfoVendedor(evaluacion.getVendedorId())
                .defaultIfEmpty(fallback);
    }

    private Mono<TiendaInfo> resolverTienda(Evaluacion evaluacion) {
        TiendaInfo fallback = new TiendaInfo(null, null);
        if (evaluacion.getTiendaId() == null) {
            return Mono.just(fallback);
        }
        return tiendaPort.obtenerInfoTienda(evaluacion.getTiendaId())
                .defaultIfEmpty(fallback);
    }
}
