package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.infrastructure.reconciliation.FinanzasReconciliadorStartup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint de uso exclusivo para administración interna.
 * Permite disparar manualmente el reconciliador de finanzas
 * desde un entorno local con conexión directa a Firestore,
 * evitando el problema de TLS/gRPC en Cloud Run cold-start.
 *
 * POST /api/admin/finanzas/reconciliar
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/finanzas")
@RequiredArgsConstructor
public class ReconciliadorAdminController {

    private final FinanzasReconciliadorStartup reconciliador;

    @PostMapping("/reconciliar")
    public Mono<ResponseEntity<String>> reconciliar() {
        log.info("[Admin] Reconciliación manual iniciada");
        return reconciliador.ejecutarManual()
                .map(msg -> ResponseEntity.ok(msg));
    }
}
