package com.motoyav2.cobranza.application.port.in.command;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;

import java.util.List;

public record IniciarCasoCommand(
        String contratoId,
        String storeId,
        DatosTitularDocument titular,
        String motoDescripcion,
        Double capitalOriginal,
        Double saldoActual,
        String nivelEstrategia,
        String estadoCaso,
        String agenteAsignadoId,
        String agenteAsignadoNombre,
        String fechaVencimientoPrimerCuotaImpaga,  // ISO date string "YYYY-MM-DD"
        List<CuotaCronogramaDocument> cronograma,
        String creadoPor
) {}
