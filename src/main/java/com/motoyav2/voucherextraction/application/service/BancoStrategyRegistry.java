package com.motoyav2.voucherextraction.application.service;

import com.motoyav2.voucherextraction.domain.strategy.BancoStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registro de estrategias de extracción por banco.
 * Spring inyecta automáticamente todas las implementaciones de BancoStrategy
 * en el orden definido por @Order. Para agregar un banco nuevo basta crear
 * una clase @Component que implemente BancoStrategy — este registry no cambia.
 */
@Component
public class BancoStrategyRegistry {

    private final List<BancoStrategy> strategies;

    public BancoStrategyRegistry(List<BancoStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Devuelve la primera estrategia que soporta el texto del voucher.
     * GenéricoStrategy tiene @Order(MAX_VALUE) por lo que siempre hay una respuesta.
     */
    public BancoStrategy findStrategy(String fullText) {
        return strategies.stream()
                .filter(s -> s.soporta(fullText))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró estrategia — GenéricoStrategy debe siempre estar presente"));
    }
}
