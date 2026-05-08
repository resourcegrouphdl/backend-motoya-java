package com.motoyav2.cobranza.application.dto;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AcuerdoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;

import java.util.List;

/**
 * DTO de respuesta para la Vista 360 de un caso de cobranza.
 * Agrega el caso raíz con todas sus sub-colecciones más datos del vehículo financiado.
 * {@code vehiculo} puede ser null si el contrato aún no tiene factura cargada.
 */
public record Vista360CasoDto(
        CasoCobranzaDocument caso,
        List<EventoCobranzaDocument> eventos,
        List<MovimientoDocument> movimientos,
        List<PromesaDocument> promesas,
        List<AcuerdoDocument> acuerdos,
        VehiculoDto vehiculo,
        List<VoucherVista360Dto> vouchers
) {}
