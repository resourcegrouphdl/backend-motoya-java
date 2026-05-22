package com.motoyav2.cobranza.infrastructure.adapter.in.web;

import com.motoyav2.cobranza.application.service.VoucherSueltoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cobranzas/vouchers-sueltos")
public class VoucherSueltoController {

    private final VoucherSueltoService voucherSueltoService;

    @GetMapping
    public Flux<Map<String, Object>> listar(
            @RequestParam(defaultValue = "PENDIENTE") String estado) {
        return voucherSueltoService.listar(estado);
    }

    @GetMapping("/{id}")
    public Mono<Map<String, Object>> obtener(@PathVariable String id) {
        return voucherSueltoService.obtener(id);
    }

    @PostMapping("/{id}/asociar")
    public Mono<Map<String, Object>> asociar(
            @PathVariable String id,
            @RequestBody AsociarRequest req,
            ServerWebExchange exchange) {
        String agenteId = (String) exchange.getAttributes().get("userId");
        return voucherSueltoService.asociar(id, req.contratoId(), req.guardarTelefono(), agenteId)
            .map(voucherId -> Map.of("status", "OK", "voucherId", voucherId));
    }

    @PostMapping("/{id}/descartar")
    public Mono<Map<String, Object>> descartar(
            @PathVariable String id,
            @RequestBody(required = false) DescartarRequest req) {
        return voucherSueltoService.descartar(id, req != null ? req.motivo() : null)
            .thenReturn(Map.of("status", "OK"));
    }

    record AsociarRequest(String contratoId, boolean guardarTelefono) {}
    record DescartarRequest(String motivo) {}
}
