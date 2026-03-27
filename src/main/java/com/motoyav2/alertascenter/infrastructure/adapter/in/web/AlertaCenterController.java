package com.motoyav2.alertascenter.infrastructure.adapter.in.web;

import com.motoyav2.alertascenter.application.dto.AlertaResponse;
import com.motoyav2.alertascenter.application.dto.DeclinarCasoRequest;
import com.motoyav2.alertascenter.application.dto.EventoAlertaRequest;
import com.motoyav2.alertascenter.application.dto.RegistrarTokenRequest;
import com.motoyav2.alertascenter.application.service.AlertaCenterService;
import com.motoyav2.shared.security.FirebaseAuthenticationToken;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST API del Centro de Alertas Internas.
 *
 * Rutas públicas (token interno):
 *   POST /api/v1/alertas/evento  ← Cloud Function
 *
 * Rutas protegidas (Firebase Auth):
 *   GET    /api/v1/alertas
 *   GET    /api/v1/alertas/{id}
 *   POST   /api/v1/alertas/{id}/tomar
 *   POST   /api/v1/alertas/{id}/declinar
 *   POST   /api/v1/alertas/tokens
 *   DELETE /api/v1/alertas/tokens/{token}
 */
@RestController
@RequestMapping("/api/v1/alertas")
@RequiredArgsConstructor
@Slf4j
public class AlertaCenterController {

    private final AlertaCenterService alertaCenterService;

    @Value("${alertas.internal.token:alertas-internal-dev-token}")
    private String internalToken;

    // ─────────────────────────────────────────────────────────────────────────
    // EVENTO (Cloud Function → Backend)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/evento")
    public Mono<ResponseEntity<AlertaResponse>> procesarEvento(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody @Valid EventoAlertaRequest request) {

        if (!internalToken.equals(token)) {
            log.warn("Intento de acceso a /evento con token inválido");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        return alertaCenterService.procesarEvento(request.tipo(), request.subTipo(), request.fuenteId())
                .map(alerta -> ResponseEntity.status(HttpStatus.CREATED).body(AlertaResponse.from(alerta)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    public Flux<AlertaResponse> listar(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String estado) {
        return alertaCenterService.listarAlertas(limit, estado)
                .map(AlertaResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<AlertaResponse>> obtener(@PathVariable String id) {
        return alertaCenterService.obtenerAlerta(id)
                .map(alerta -> ResponseEntity.ok(AlertaResponse.from(alerta)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACCIONES
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/tomar")
    public Mono<ResponseEntity<AlertaResponse>> tomarCaso(
            @PathVariable String id,
            Authentication authentication) {

        FirebaseUserDetails user = extraerUsuario(authentication);
        String nombre = resolverNombre(user);

        return alertaCenterService.tomarCaso(id, user.uid(), user.email(), nombre)
                .map(alerta -> ResponseEntity.ok(AlertaResponse.from(alerta)));
    }

    @PostMapping("/{id}/declinar")
    public Mono<ResponseEntity<AlertaResponse>> declinarCaso(
            @PathVariable String id,
            @RequestBody(required = false) DeclinarCasoRequest request,
            Authentication authentication) {

        FirebaseUserDetails user = extraerUsuario(authentication);
        String nombre = resolverNombre(user);
        String motivo = request != null ? request.motivo() : null;

        return alertaCenterService.declinarCaso(id, user.uid(), user.email(), nombre, motivo)
                .map(alerta -> ResponseEntity.ok(AlertaResponse.from(alerta)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOKENS FCM
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/tokens")
    public Mono<ResponseEntity<Void>> registrarToken(
            @RequestBody @Valid RegistrarTokenRequest request,
            Authentication authentication) {

        FirebaseUserDetails user = extraerUsuario(authentication);
        return alertaCenterService.registrarToken(user.uid(), user.email(), request.token())
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    @DeleteMapping("/tokens/{token}")
    public Mono<ResponseEntity<Void>> eliminarToken(@PathVariable String token) {
        return alertaCenterService.eliminarToken(token)
                .thenReturn(ResponseEntity.<Void>noContent().<Void>build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private FirebaseUserDetails extraerUsuario(Authentication authentication) {
        if (authentication instanceof FirebaseAuthenticationToken token) {
            return (FirebaseUserDetails) token.getPrincipal();
        }
        throw new IllegalStateException("Autenticación Firebase requerida");
    }

    private String resolverNombre(FirebaseUserDetails user) {
        if (user.claims() != null) {
            Object name = user.claims().get("name");
            if (name != null && !name.toString().isBlank()) return name.toString();
        }
        return user.email();
    }
}
