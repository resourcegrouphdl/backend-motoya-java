package com.motoyav2.debug;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/debug/clientes")
public class BuscarClienteController {

    private final BuscarClienteService buscarClienteService;

    @GetMapping("/buscar-telefono")
    public Flux<Map<String, Object>> buscarPorTelefono(@RequestParam String telefono) {
        return buscarClienteService.buscarPorTelefono(telefono);
    }
}