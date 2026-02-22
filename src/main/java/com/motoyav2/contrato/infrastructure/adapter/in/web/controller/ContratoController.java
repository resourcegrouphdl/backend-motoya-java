package com.motoyav2.contrato.infrastructure.adapter.in.web.controller;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.domain.port.in.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.mapper.ContratoResponseMapper;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static reactor.netty.http.HttpConnectionLiveness.log;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Log4j2
public class ContratoController {

    private final ListarContratosUseCase listarContratosUseCase;
    private final ObtenerContratoUseCase obtenerContratoUseCase;
    private final CrearContratoUseCase crearContratoUseCase;
    private final ValidarDocumentoUseCase validarDocumentoUseCase;
    private final AprobarContratoUseCase aprobarContratoUseCase;
    private final RechazarContratoUseCase rechazarContratoUseCase;

    @GetMapping("/contratos/lista")
    public Flux<ContratoListItemDto> listar() {
        return listarContratosUseCase.listar()
                .map(ContratoResponseMapper::toListItemDto);
    }

    @GetMapping("/contratos/{id}")
    public Mono<ContratoResponse> obtener(@PathVariable String id) {
        return obtenerContratoUseCase.obtenerPorId(id)
                .map(ContratoResponseMapper::toResponse);
    }

    @PostMapping("/contract")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ContratoResponse> crear(
            @Valid @RequestBody CrearContratoManualRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {


        DatosTitular titular = new DatosTitular(
                request.titular().nombres(), request.titular().apellidos(),
                request.titular().tipoDocumento(), request.titular().numeroDocumento(),
                request.titular().telefono(), request.titular().email(),
                request.titular().direccion(), request.titular().distrito(),
                request.titular().provincia(), request.titular().departamento()
        );

        DatosFiador fiador = null;
        if (request.fiador() != null) {
            fiador = new DatosFiador(
                    request.fiador().nombres(), request.fiador().apellidos(),
                    request.fiador().tipoDocumento(), request.fiador().numeroDocumento(),
                    request.fiador().telefono(), request.fiador().email(),
                    request.fiador().direccion(), request.fiador().distrito(),
                    request.fiador().provincia(), request.fiador().departamento(),
                    request.fiador().parentesco()
            );
        }

        TiendaInfo tienda = new TiendaInfo(
                request.tienda().tiendaId(), request.tienda().nombreTienda(),
                request.tienda().direccion(), request.tienda().ciudad()
        );

        DatosFinancieros financieros = new DatosFinancieros(
                request.datosFinancieros().precioVehiculo(), request.datosFinancieros().cuotaInicial(),
                request.datosFinancieros().montoFinanciado(), request.datosFinancieros().tasaInteresAnual(),
                request.datosFinancieros().numeroCuotas(), request.datosFinancieros().cuotaMensual()
        );
        FacturaVehiculo factura = FacturaVehiculo.builder()
            .marcaVehiculo(request.datosFinancieros().marcaVehiculo())
            .modeloVehiculo(request.datosFinancieros().modeloVehiculo())
            .anioVehiculo(Integer.valueOf(request.datosFinancieros().anioVehiculo()))
            .colorVehiculo(request.datosFinancieros().colorVehiculo())
            .estadoValidacion(EstadoValidacion.PENDIENTE)
            .build();


        log.info("se Crea el Contrato con id" + request.evaluacionId());

        return crearContratoUseCase.crear(titular, fiador, tienda, financieros, principal.uid(), factura, request.evaluacionId() )
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/documento/{tipo}/validar")
    public Mono<ContratoResponse> validarDocumento(
            @PathVariable String id,
            @PathVariable String tipo,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO
                : EstadoValidacion.RECHAZADO;
        String observaciones = request.observaciones() != null ? request.observaciones() : "";
        return validarDocumentoUseCase.validar(id, tipo, estado, observaciones, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/aprobar")
    public Mono<ContratoResponse> aprobar(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        return aprobarContratoUseCase.aprobar(id, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/rechazar")
    public Mono<ContratoResponse> rechazar(
            @PathVariable String id,
            @Valid @RequestBody RechazarContratoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        return rechazarContratoUseCase.rechazar(id, request.motivo(), principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }
}
