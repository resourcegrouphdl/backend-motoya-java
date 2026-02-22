package com.motoyav2.contrato.infrastructure.adapter.in.web.controller;

import com.motoyav2.contrato.domain.model.BoucherPagoInicial;
import com.motoyav2.contrato.domain.model.ContratoListItem;
import com.motoyav2.contrato.domain.model.EvidenciaFirma;
import com.motoyav2.contrato.domain.model.EvidenciaRepsonse;
import com.motoyav2.contrato.domain.model.FacturaVehiculo;
import com.motoyav2.contrato.domain.port.in.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.mapper.ContratoParaTiendaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/store")
public class StoreContractsController {

    private final ListarContratosUseCase listarContratosUseCase;
    private final ObtenerContratoUseCase obtenerContratoUseCase;
    private final SubirBoucherUseCase subirBoucherUseCase;
    private final SubirFacturaUseCase subirFacturaUseCase;
    private final SubirEvidenciaUseCase subirEvidenciaUseCase;
    private final GenerarContratoPdfUseCase generarContratoPDF;

    @GetMapping("/{storeid}")
    public Flux<ContratoListItem> listar(@PathVariable String storeid) {
        return listarContratosUseCase.listarrContratosPorTienda(storeid);
    }

    @GetMapping("/contrato/{id}")
    public Mono<ContratoDetalleAPIDto> obtenerporId(@PathVariable String id) {
        return obtenerContratoUseCase.obtenerPorId(id)
                .map(ContratoParaTiendaResponse::toResponse);
    }

    @PostMapping("/contrato/{id}/boucher")
    public Mono<UploadBoucherResponse> boucherUpload(@PathVariable String id,
            @RequestBody BoucherUploadRequest boucherUploadRequest) {

        BoucherPagoInicial boucher = BoucherPagoInicial.builder()

                .urlDocumento(boucherUploadRequest.urlDocumento())
                .nombreArchivo(boucherUploadRequest.nombreArchivo())
                .tipoArchivo(boucherUploadRequest.tipoArchivo())
                .tamanioBytes(boucherUploadRequest.tamanioBytes())
                .build();

        return subirBoucherUseCase.subir(id, boucher)
                .map(saved -> UploadBoucherResponse.builder()
                        .message("Boucher subido exitosamente")
                        .boucherId(saved.id())
                        .build());
    }

    @PostMapping("/contrato/{id}/factura")
    public Mono<UploadFacturaResponse> facturaUpload(@PathVariable String id,
                                                     @RequestBody FacturaUploadRequest facturaUploadRequest) {

        FacturaVehiculo factura = FacturaVehiculo.builder()
                .numeroFactura(facturaUploadRequest.numeroFactura())
                .urlDocumento(facturaUploadRequest.urlDocumento())
                .nombreArchivo(facturaUploadRequest.nombreArchivo())
                .tipoArchivo(facturaUploadRequest.tipoArchivo())
                .tamanioBytes(facturaUploadRequest.tamanioBytes())
                .marcaVehiculo(facturaUploadRequest.marcaVehiculo())
                .modeloVehiculo(facturaUploadRequest.modeloVehiculo())
                .anioVehiculo(facturaUploadRequest.anioVehiculo())
                .colorVehiculo(facturaUploadRequest.colorVehiculo())
                .serieMotor(facturaUploadRequest.serieMotor())
                .serieChasis(facturaUploadRequest.serieChasis())
                .build();

        return subirFacturaUseCase.subir(id, factura)
                .map(saved -> new UploadFacturaResponse(
                        "Factura subida exitosamente",
                        saved.id()));
    }

    @PostMapping("/contrato/{id}/evidencia")
    public Mono<EvidenciaRepsonse> uploadEvidencias(@PathVariable String id,
                                                    @RequestBody EvidenciaUploadRequest evidenciaUploadRequest) {

        EvidenciaFirma evidencia = EvidenciaFirma.builder()
                .tipoEvidencia(evidenciaUploadRequest.tipoEvidencia())
                .urlEvidencia(evidenciaUploadRequest.urlEvidencia())
                .nombreArchivo(evidenciaUploadRequest.nombreArchivo())
                .tipoArchivo(evidenciaUploadRequest.tipoArchivo())
                .tamanioBytes(evidenciaUploadRequest.tamanioBytes())
                .descripcion(evidenciaUploadRequest.descripcion())
                .build();

        return subirEvidenciaUseCase.subir(id, evidencia)
                .map(saved -> new EvidenciaRepsonse(
                        "Evidencia subida exitosamente",
                        saved.id()));
    }

    @PostMapping("/contrato/{id}/generar")
    public Mono<GenerarContratoresponseDto> generarContratoPdf(@PathVariable String id) {
        return generarContratoPDF.documentosGenerados(id)
                .collectList()
                .map(docs -> new GenerarContratoresponseDto(
                        "Contrato generado exitosamente",
                        docs.stream()
                                .map(d -> new DocumentoGeneradoAPIDto(d.tipo().name(), d.urlDocumento()))
                                .toList()
                ));
    }

}
