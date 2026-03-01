package com.motoyav2.contrato.infrastructure.adapter.in.web.controller;

import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.domain.port.in.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.mapper.ContratoParaTiendaResponse;
import jakarta.validation.Valid;
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
    private final RegistrarNumeroDeTituloUseCase registrarNumeroDeTituloUseCase;
    private final SubirDocumentoPostFirmaUseCase subirDocumentoPostFirmaUseCase;

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
                .map(saved -> {
                    String boucherId = saved.boucheresPagoInicial().getLast().id();
                    return UploadBoucherResponse.builder()
                            .message("Boucher subido exitosamente")
                            .boucherId(boucherId)
                            .bouchers(ContratoParaTiendaResponse.mapBouchers(saved.boucheresPagoInicial()))
                            .build();
                });
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

    // ── Endpoints post-firma ──────────────────────────────────────────────────

    @PostMapping("/contrato/{contratoId}/numero-titulo")
    public Mono<ContratoDetalleAPIDto> subirNumeroDeTitulo(
            @PathVariable String contratoId,
            @Valid @RequestBody NumeroDeTituloRequest dto
    ) {
        return registrarNumeroDeTituloUseCase.registrar(contratoId, dto.numeroDeTitulo())
                .map(ContratoParaTiendaResponse::toResponse);
    }

    @PostMapping("/contrato/{contratoId}/tive")
    public Mono<ContratoDetalleAPIDto> subirTIVE(
            @PathVariable String contratoId,
            @RequestBody EvidenciaDocumentoRequest dto
    ) {
        return subirDocumentoPostFirmaUseCase.subir(contratoId, "TIVE", toEvidenciaDocumento(dto))
                .map(ContratoParaTiendaResponse::toResponse);
    }

    @PostMapping("/contrato/{contratoId}/evidencia-soat")
    public Mono<ContratoDetalleAPIDto> subirSOAT(
            @PathVariable String contratoId,
            @RequestBody EvidenciaDocumentoRequest dto
    ) {
        return subirDocumentoPostFirmaUseCase.subir(contratoId, "SOAT", toEvidenciaDocumento(dto))
                .map(ContratoParaTiendaResponse::toResponse);
    }

    @PostMapping("/contrato/{contratoId}/evidencia-placa-rodaje")
    public Mono<ContratoDetalleAPIDto> subirPlacaRodaje(
            @PathVariable String contratoId,
            @RequestBody EvidenciaDocumentoRequest dto
    ) {
        return subirDocumentoPostFirmaUseCase.subir(contratoId, "PLACA_RODAJE", toEvidenciaDocumento(dto))
                .map(ContratoParaTiendaResponse::toResponse);
    }

    @PostMapping("/contrato/{contratoId}/acta-entrega")
    public Mono<ContratoDetalleAPIDto> subirActaDeEntrega(
            @PathVariable String contratoId,
            @RequestBody EvidenciaDocumentoRequest dto
    ) {
        return subirDocumentoPostFirmaUseCase.subir(contratoId, "ACTA_ENTREGA", toEvidenciaDocumento(dto))
                .map(ContratoParaTiendaResponse::toResponse);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EvidenciaDocumento toEvidenciaDocumento(EvidenciaDocumentoRequest dto) {
        return EvidenciaDocumento.builder()
                .tipoEvidencia(dto.tipoEvidencia())
                .urlEvidencia(dto.urlEvidencia())
                .nombreArchivo(dto.nombreArchivo())
                .tipoArchivo(dto.tipoArchivo())
                .tamanioBytes(dto.tamanioBytes())
                .descripcion(dto.descripcion())
                .build();
    }
}

// se ha desabilitaodo el clain de reconocimeinto por el moment