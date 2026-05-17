package com.motoyav2.contabilidad.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ContratosPreviewResponse(
        LocalDate desde,
        LocalDate hasta,
        List<ContratosTiendaDTO> tiendas,
        int totalContratos,
        Map<String, Integer> porEstado
) {}
