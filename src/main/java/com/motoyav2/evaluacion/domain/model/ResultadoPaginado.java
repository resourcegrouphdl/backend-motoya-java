package com.motoyav2.evaluacion.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ResultadoPaginado<T> {

    private final List<T> items;
    private final long total;
    private final int pagina;
    private final int porPagina;
}
