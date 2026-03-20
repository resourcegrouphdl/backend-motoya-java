package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.application.dto.PagedResult;
import com.motoyav2.evaluacion.application.dto.SolicitudTrackingDto;
import com.motoyav2.evaluacion.domain.port.in.ListarSolicitudesVendedorUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarSolicitudesVendedorUseCaseImpl implements ListarSolicitudesVendedorUseCase {

    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<PagedResult<SolicitudTrackingDto>> ejecutar(String vendedorId, int page, int size) {
        int offset = page * size;

        Mono<Long> totalMono = solicitudRepository.countByVendedorId(vendedorId);
        Mono<List<SolicitudTrackingDto>> listMono = solicitudRepository
                .findByVendedorId(vendedorId, size, offset)
                .map(SolicitudTrackingDto::from)
                .collectList();

        return Mono.zip(listMono, totalMono)
                .map(tuple -> PagedResult.of(tuple.getT1(), page, size, tuple.getT2()));
    }
}
