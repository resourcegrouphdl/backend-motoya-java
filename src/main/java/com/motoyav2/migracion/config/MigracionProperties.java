package com.motoyav2.migracion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuración del módulo de migración asistida.
 * Prefijo: migracion
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "migracion")
public class MigracionProperties {

    /** ID del calendario "clientes" de motoyadigital@gmail.com */
    private String calendarId;

    /**
     * colorIds de Google Calendar que indican cuota pagada.
     * Default: 2 (Sage/verde) y 10 (Basil/verde oscuro)
     */
    private List<String> colorPagadoIds = List.of("2", "10");
}
