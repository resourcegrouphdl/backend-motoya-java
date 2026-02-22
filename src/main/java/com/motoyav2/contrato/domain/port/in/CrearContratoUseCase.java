package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.*;
import reactor.core.publisher.Mono;

public interface CrearContratoUseCase {

    Mono<Contrato> crear(
        DatosTitular titular,
        DatosFiador fiador,
        TiendaInfo tienda,
        DatosFinancieros datosFinancieros,
        String creadoPor,
        FacturaVehiculo factura,
        String evaluacionId

    );
}
