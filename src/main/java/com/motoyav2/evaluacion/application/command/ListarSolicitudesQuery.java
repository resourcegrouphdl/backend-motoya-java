package com.motoyav2.evaluacion.application.command;

public record ListarSolicitudesQuery(
        String estado,
        String prioridad,
        String search,
        int page,
        int size
) {
    public ListarSolicitudesQuery {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
    }
}
