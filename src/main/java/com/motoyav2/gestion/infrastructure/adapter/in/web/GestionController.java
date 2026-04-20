package com.motoyav2.gestion.infrastructure.adapter.in.web;

import com.motoyav2.gestion.application.GestionService;
import com.motoyav2.gestion.domain.model.ModuloPermiso;
import com.motoyav2.gestion.infrastructure.adapter.in.web.dto.*;
import com.motoyav2.shared.security.FirebaseUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gestion")
@RequiredArgsConstructor
public class GestionController {

    private final GestionService gestionService;

    // ══════════════════════════════════════════════════════════════════════════
    // CATÁLOGO DE MÓDULOS (sin autenticación de rol especial — cualquier interno)
    // ══════════════════════════════════════════════════════════════════════════

    /** Devuelve la lista de todos los módulos disponibles del sistema */
    @GetMapping("/modulos")
    public Mono<Map<String, List<String>>> listarModulos() {
        return Mono.just(Map.of("modulos", ModuloPermiso.ALL));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USUARIOS INTERNOS — solo ADMIN
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/usuarios-internos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UsuarioInternoResponse> crearUsuarioInterno(
            @Valid @RequestBody CrearUsuarioInternoRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {
        return gestionService.crearUsuarioInterno(req, principal.uid());
    }

    @GetMapping("/usuarios-internos")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UsuarioInternoResponse> listarUsuariosInternos() {
        return gestionService.listarUsuariosInternos();
    }

    @GetMapping("/usuarios-internos/{uid}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UsuarioInternoResponse> obtenerUsuarioInterno(@PathVariable String uid) {
        return gestionService.obtenerUsuarioInterno(uid);
    }

    @PutMapping("/usuarios-internos/{uid}/activo")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UsuarioInternoResponse> actualizarEstadoUsuario(
            @PathVariable String uid,
            @RequestParam boolean activo,
            @AuthenticationPrincipal FirebaseUserDetails principal) {
        return gestionService.actualizarEstadoUsuario(uid, activo, principal.uid());
    }

    /**
     * Actualiza módulos del usuario y fuerza re-login inmediato.
     */
    @PutMapping("/usuarios-internos/{uid}/permisos")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> actualizarPermisos(
            @PathVariable String uid,
            @Valid @RequestBody ActualizarPermisosRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {
        return gestionService.actualizarPermisos(uid, req, principal.uid());
    }

    /**
     * Envía email de reset de contraseña (Firebase gestiona el enlace).
     */
    @PostMapping("/usuarios-internos/{uid}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> enviarResetPassword(@PathVariable String uid) {
        return gestionService.enviarResetPassword(uid);
    }

    /**
     * Admin setea contraseña directamente (sin necesidad de email).
     */
    @PutMapping("/usuarios-internos/{uid}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> setPassword(
            @PathVariable String uid,
            @Valid @RequestBody SetPasswordRequest req) {
        return gestionService.setPassword(uid, req);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TIENDAS — solo ADMIN
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/tiendas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<TiendaResponse> crearTienda(
            @Valid @RequestBody CrearTiendaRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {
        return gestionService.crearTienda(req, principal.uid());
    }

    @GetMapping("/tiendas")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<TiendaResponse> listarTiendas() {
        return gestionService.listarTiendas();
    }

    @GetMapping("/tiendas/{uid}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<TiendaResponse> obtenerTienda(@PathVariable String uid) {
        return gestionService.obtenerTienda(uid);
    }

    @PutMapping("/tiendas/{uid}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<TiendaResponse> cambiarEstadoTienda(
            @PathVariable String uid,
            @Valid @RequestBody CambiarEstadoRequest req) {
        return gestionService.cambiarEstadoTienda(uid, req);
    }

    @GetMapping("/tiendas/{uid}/vendedores")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<VendedorResponse> listarVendedoresDeTienda(@PathVariable String uid) {
        return gestionService.listarVendedoresDeTienda(uid);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VENDEDORES — solo ADMIN
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/vendedores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<VendedorResponse> crearVendedor(
            @Valid @RequestBody CrearVendedorRequest req,
            @AuthenticationPrincipal FirebaseUserDetails principal) {
        return gestionService.crearVendedor(req, principal.uid());
    }

    @GetMapping("/vendedores")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<VendedorResponse> listarVendedores(
            @RequestParam(required = false) String tiendaId) {
        return gestionService.listarVendedores(tiendaId);
    }

    @GetMapping("/vendedores/{uid}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<VendedorResponse> obtenerVendedor(@PathVariable String uid) {
        return gestionService.obtenerVendedor(uid);
    }

    @PutMapping("/vendedores/{uid}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<VendedorResponse> cambiarEstadoVendedor(
            @PathVariable String uid,
            @Valid @RequestBody CambiarEstadoRequest req) {
        return gestionService.cambiarEstadoVendedor(uid, req);
    }

    @PostMapping("/vendedores/{uid}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> resetPasswordVendedor(@PathVariable String uid) {
        return gestionService.enviarResetPassword(uid);
    }

    @PutMapping("/vendedores/{uid}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> setPasswordVendedor(
            @PathVariable String uid,
            @Valid @RequestBody SetPasswordRequest req) {
        return gestionService.setPassword(uid, req);
    }
}
