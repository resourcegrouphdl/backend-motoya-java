package com.motoyav2.contrato.infrastructure.adapter.in.web.controller;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.*;
import com.motoyav2.contrato.domain.port.in.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.dto.*;
import com.motoyav2.contrato.infrastructure.adapter.in.web.mapper.ContratoResponseMapper;
import com.motoyav2.evaluacion.application.dto.NombreResuelto;
import com.motoyav2.evaluacion.application.service.NombreVerificadoResolver;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final ValidarEvidenciaDocumentoUseCase validarEvidenciaDocumentoUseCase;
    private final ValidarEvidenciaFirmaUseCase validarEvidenciaFirmaUseCase;
    private final ConfirmarFirmaUseCase confirmarFirmaUseCase;
    private final CompletarContratoUseCase completarContratoUseCase;

    // ── Resolución de nombres verificados (RENIEC) ─────────────────────────
    private final NombreVerificadoResolver nombreVerificadoResolver;
    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository clienteRepository;

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
        log.info("Creando contrato para evaluacionId={}", request.evaluacionId());

        // ── 1. Resolver nombres verificados (RENIEC) para titular y fiador ──
        //    Se buscan los IDs de cliente a partir de la solicitud (evaluacionId).
        //    Si la búsqueda falla o la verificación no fue exitosa, se aplica
        //    graceful fallback a los nombres del formulario enviado por el frontend.
        Mono<NombreResuelto> nombreTitularMono = resolverNombrePorSolicitud(
                request.evaluacionId(), false,
                request.titular().nombres(), request.titular().apellidos());

        Mono<NombreResuelto> nombreFiadorMono = (request.fiador() != null)
                ? resolverNombrePorSolicitud(
                        request.evaluacionId(), true,
                        request.fiador().nombres(), request.fiador().apellidos())
                : Mono.just(new NombreResuelto("", "", false));

        return Mono.zip(nombreTitularMono, nombreFiadorMono)
                .flatMap(tuple -> {
                    NombreResuelto nTitular = tuple.getT1();
                    NombreResuelto nFiador  = tuple.getT2();

                    if (nTitular.desdeReniec()) {
                        log.info("Titular: nombres tomados de RENIEC → '{}' '{}'",
                                nTitular.nombres(), nTitular.apellidos());
                    }

                    // ── 2. Construir objetos de dominio ──────────────────
                    DatosTitular titular = new DatosTitular(
                            nTitular.nombres(), nTitular.apellidos(),
                            request.titular().tipoDocumento(), request.titular().numeroDocumento(),
                            request.titular().telefono(), request.titular().email(),
                            request.titular().direccion(), request.titular().distrito(),
                            request.titular().provincia(), request.titular().departamento()
                    );

                    DatosFiador fiador = null;
                    if (request.fiador() != null) {
                        fiador = new DatosFiador(
                                nFiador.nombres(), nFiador.apellidos(),
                                request.fiador().tipoDocumento(), request.fiador().numeroDocumento(),
                                request.fiador().telefono(), request.fiador().email(),
                                request.fiador().direccion(), request.fiador().distrito(),
                                request.fiador().provincia(), request.fiador().departamento(),
                                request.fiador().parentesco()
                        );
                        if (nFiador.desdeReniec()) {
                            log.info("Fiador: nombres tomados de RENIEC → '{}' '{}'",
                                    nFiador.nombres(), nFiador.apellidos());
                        }
                    }

                    TiendaInfo tienda = new TiendaInfo(
                            request.tienda().tiendaId(), request.tienda().nombreTienda(),
                            request.tienda().direccion(), request.tienda().ciudad()
                    );

                    DatosFinancieros financieros = new DatosFinancieros(
                            request.datosFinancieros().precioVehiculo(),
                            request.datosFinancieros().cuotaInicial(),
                            request.datosFinancieros().montoFinanciado(),
                            request.datosFinancieros().tasaInteresAnual(),
                            request.datosFinancieros().numeroCuotas(),
                            request.datosFinancieros().cuotaMensual()
                    );

                    FacturaVehiculo factura = FacturaVehiculo.builder()
                            .marcaVehiculo(request.datosFinancieros().marcaVehiculo())
                            .modeloVehiculo(request.datosFinancieros().modeloVehiculo())
                            .anioVehiculo(request.datosFinancieros().anioVehiculo() != null
                                    ? Integer.valueOf(request.datosFinancieros().anioVehiculo()) : null)
                            .colorVehiculo(request.datosFinancieros().colorVehiculo())
                            .estadoValidacion(EstadoValidacion.PENDIENTE)
                            .build();

                    return crearContratoUseCase.crear(
                            titular, fiador, tienda, financieros,
                            principal.uid(), factura, request.evaluacionId());
                })
                .map(ContratoResponseMapper::toResponse);
    }

    /**
     * Resuelve el nombre verificado de un cliente (titular o fiador) a partir de la solicitud.
     * Si no se encuentra la solicitud, el cliente o la verificación, retorna el nombre del formulario.
     *
     * @param evaluacionId   ID de la solicitud de evaluación
     * @param esFiador       true → buscar fiadorId; false → titularId
     * @param fallbackNombres   nombres del formulario (fallback)
     * @param fallbackApellidos apellidos del formulario (fallback)
     */
    private Mono<NombreResuelto> resolverNombrePorSolicitud(
            String evaluacionId,
            boolean esFiador,
            String fallbackNombres,
            String fallbackApellidos) {

        NombreResuelto fallback = new NombreResuelto(
                fallbackNombres != null ? fallbackNombres.trim().toUpperCase() : "",
                fallbackApellidos != null ? fallbackApellidos.trim().toUpperCase() : "",
                false);

        if (evaluacionId == null || evaluacionId.isBlank()) {
            return Mono.just(fallback);
        }

        return solicitudRepository.findById(evaluacionId)
                .flatMap(solicitud -> {
                    String clienteId = esFiador ? solicitud.getFiadorId() : solicitud.getTitularId();
                    if (clienteId == null || clienteId.isBlank()) return Mono.just(fallback);
                    return clienteRepository.findById(clienteId)
                            .map(nombreVerificadoResolver::resolverDesdeCliente)
                            .defaultIfEmpty(fallback);
                })
                .defaultIfEmpty(fallback)
                .onErrorReturn(fallback);
    }

    @PutMapping("/contratos/{id}/boucher/{boucherId}/validar")
    public Mono<ContratoResponse> validarBoucher(
            @PathVariable String id,
            @PathVariable String boucherId,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO
                : EstadoValidacion.RECHAZADO;
        String observaciones = request.observaciones() != null ? request.observaciones() : "";
        return validarDocumentoUseCase.validar(id, "BOUCHER", estado, observaciones, principal.uid(), boucherId)
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/documento/factura/validar")
    public Mono<ContratoResponse> validarFactura(
            @PathVariable String id,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO
                : EstadoValidacion.RECHAZADO;
        String observaciones = request.observaciones() != null ? request.observaciones() : "";
        return validarDocumentoUseCase.validar(id, "FACTURA", estado, observaciones, principal.uid(), null)
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

    // ── Firma ─────────────────────────────────────────────────────────────────

    @PutMapping("/contratos/{id}/evidencia-firma/{evidenciaId}/validar")
    public Mono<ContratoResponse> validarEvidenciaFirma(
            @PathVariable String id,
            @PathVariable String evidenciaId,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO : EstadoValidacion.RECHAZADO;
        String obs = request.observaciones() != null ? request.observaciones() : "";
        return validarEvidenciaFirmaUseCase.validar(id, evidenciaId, estado, obs, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/confirmar-firma")
    public Mono<ContratoResponse> confirmarFirma(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        return confirmarFirmaUseCase.confirmar(id, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    // ── Documentos post-firma ─────────────────────────────────────────────────

    @PutMapping("/contratos/{id}/tive/validar")
    public Mono<ContratoResponse> validarTive(
            @PathVariable String id,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO : EstadoValidacion.RECHAZADO;
        String obs = request.observaciones() != null ? request.observaciones() : "";
        return validarEvidenciaDocumentoUseCase.validar(id, "TIVE", estado, obs, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/evidencia-soat/validar")
    public Mono<ContratoResponse> validarSOAT(
            @PathVariable String id,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO : EstadoValidacion.RECHAZADO;
        String obs = request.observaciones() != null ? request.observaciones() : "";
        return validarEvidenciaDocumentoUseCase.validar(id, "SOAT", estado, obs, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/evidencia-placa-rodaje/validar")
    public Mono<ContratoResponse> validarPlacaRodaje(
            @PathVariable String id,
            @Valid @RequestBody ValidarDocumentoRequest request,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        EstadoValidacion estado = Boolean.TRUE.equals(request.aprobado())
                ? EstadoValidacion.APROBADO : EstadoValidacion.RECHAZADO;
        String obs = request.observaciones() != null ? request.observaciones() : "";
        return validarEvidenciaDocumentoUseCase.validar(id, "PLACA_RODAJE", estado, obs, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }

    @PutMapping("/contratos/{id}/completar")
    public Mono<ContratoResponse> completarContrato(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails principal
    ) {
        return completarContratoUseCase.completar(id, principal.uid())
                .map(ContratoResponseMapper::toResponse);
    }
}
