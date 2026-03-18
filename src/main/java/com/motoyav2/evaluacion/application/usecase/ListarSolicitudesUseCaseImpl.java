package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.application.command.ListarSolicitudesQuery;
import com.motoyav2.evaluacion.application.dto.PagedResult;
import com.motoyav2.evaluacion.application.dto.SolicitudResumenDto;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.port.in.ListarSolicitudesUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.port.out.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ListarSolicitudesUseCaseImpl implements ListarSolicitudesUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository clienteRepository;
    private final VehiculoRepository vehiculoRepository;

    @Override
    public Mono<PagedResult<SolicitudResumenDto>> ejecutar(ListarSolicitudesQuery query) {
        int offset = query.page() * query.size();

        Mono<Long> totalMono = solicitudRepository.countAll(query.estado(), query.prioridad(), query.search());
        Mono<java.util.List<SolicitudResumenDto>> listMono = solicitudRepository
                .findAll(query.estado(), query.prioridad(), query.search(), query.size(), offset)
                .flatMap(solicitud -> buildResumen(solicitud), 10)
                .collectList();

        return Mono.zip(listMono, totalMono)
                .map(tuple -> PagedResult.of(tuple.getT1(), query.page(), query.size(), tuple.getT2()));
    }

    private Mono<SolicitudResumenDto> buildResumen(Solicitud solicitud) {
        Mono<Cliente> titularMono = clienteRepository.findById(solicitud.getTitularId())
                .onErrorReturn(Cliente.builder().id(solicitud.getTitularId()).nombres("N/D").build());
        Mono<Vehiculo> vehiculoMono = vehiculoRepository.findById(solicitud.getVehiculoId())
                .onErrorReturn(Vehiculo.builder().id(solicitud.getVehiculoId()).build());

        return Mono.zip(titularMono, vehiculoMono)
                .map(tuple -> {
                    Cliente titular = tuple.getT1();
                    Vehiculo vehiculo = tuple.getT2();
                    return SolicitudResumenDto.builder()
                            .id(solicitud.getId())
                            .numeroSolicitud(solicitud.getNumeroSolicitud())
                            .codigoDeSolicitud(solicitud.getCodigoDeSolicitud())
                            .estado(solicitud.getEstado() != null ? solicitud.getEstado().getFirestoreValue() : null)
                            .prioridad(solicitud.getPrioridad())
                            .titularNombre(titular.getNombreCompleto())
                            .titularDocumento(titular.getDocumentNumber())
                            .titularTelefono(titular.getTelefono1())
                            .vehiculoDescripcion(vehiculo.getDescripcion())
                            .precioVehiculo(vehiculo.getPrecioReferencial())
                            .scoreFinal(solicitud.getScoreFinal())
                            .scoreDocumental(solicitud.getScoreDocumental())
                            .asesorAsignadoId(solicitud.getAsesorAsignadoId())
                            .vendedorNombre(solicitud.getVendedorNombre())
                            .vendedorTienda(solicitud.getVendedor() != null ? solicitud.getVendedor().getTienda() : null)
                            .certificadoGenerado(solicitud.getCertificadoGenerado())
                            .createdAt(solicitud.getCreatedAt())
                            .updatedAt(solicitud.getUpdatedAt())
                            .build();
                });
    }
}
