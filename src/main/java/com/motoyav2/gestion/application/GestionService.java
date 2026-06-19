package com.motoyav2.gestion.application;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.document.UserDocument;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.repository.FirestoreUserRepository;
import com.motoyav2.gestion.domain.model.ModuloPermiso;
import com.motoyav2.gestion.infrastructure.adapter.in.web.dto.*;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.document.TiendaProfileDocument;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.document.VendedorProfileDocument;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository.TiendaProfileRepository;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository.VendedorProfileRepository;
import com.motoyav2.shared.exception.BadRequestException;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GestionService {

    private final FirebaseAuth firebaseAuth;
    private final Firestore firestore;
    private final FirestoreUserRepository userRepository;
    private final TiendaProfileRepository tiendaRepository;
    private final VendedorProfileRepository vendedorRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:notificaciones@motoya.com.pe}")
    private String mailFrom;  // @Value no usa constructor → Spring lo inyecta por setter post-construcción

    // ══════════════════════════════════════════════════════════════════════════
    // USUARIOS INTERNOS (admin, supervisor, evaluador, asesor)
    // ══════════════════════════════════════════════════════════════════════════

    public Mono<UsuarioInternoResponse> crearUsuarioInterno(CrearUsuarioInternoRequest req, String adminUid) {
        return crearFirebaseUser(req.email(), req.password(), req.firstName(), req.lastName())
                .flatMap(uid -> {
                    List<String> modulos = (req.modulos() != null && !req.modulos().isEmpty())
                            ? req.modulos()
                            : ModuloPermiso.defaultForRole(req.userType());

                    return setCustomClaims(uid, req.userType(), modulos)
                            .then(guardarUsuarioInterno(uid, req, modulos, adminUid));
                });
    }

    public Flux<UsuarioInternoResponse> listarUsuariosInternos() {
        return userRepository.findAll()
                .filter(doc -> "interno".equals(doc.getUserCategory()))
                .map(this::toUsuarioInternoResponse);
    }

    public Mono<UsuarioInternoResponse> obtenerUsuarioInterno(String uid) {
        return userRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + uid)))
                .map(this::toUsuarioInternoResponse);
    }

    public Mono<UsuarioInternoResponse> actualizarEstadoUsuario(String uid, boolean activo, String adminUid) {
        return userRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + uid)))
                .flatMap(doc -> Mono.fromCallable(() -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("isActive", activo);
                    updates.put("updatedAt", Timestamp.now());
                    firestore.collection("users").document(uid).update(updates).get();
                    doc.setIsActive(activo);
                    return doc;
                }).subscribeOn(Schedulers.boundedElastic()))
                .map(this::toUsuarioInternoResponse);
    }

    /**
     * Actualiza los módulos del usuario y fuerza re-login inmediato:
     * 1. Actualiza Firestore
     * 2. Actualiza Firebase Custom Claims
     * 3. Revoca los refresh tokens (el usuario debe re-autenticarse)
     */
    public Mono<Void> actualizarPermisos(String uid, ActualizarPermisosRequest req, String adminUid) {
        // Validar que los módulos existen
        List<String> invalidModulos = req.modulos().stream()
                .filter(m -> !ModuloPermiso.ALL.contains(m))
                .toList();
        if (!invalidModulos.isEmpty()) {
            return Mono.error(new BadRequestException("Módulos inválidos: " + invalidModulos));
        }

        return userRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + uid)))
                .flatMap(doc -> Mono.fromCallable(() -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("modulos", req.modulos());
                    updates.put("updatedAt", Timestamp.now());
                    firestore.collection("users").document(uid).update(updates).get();
                    setCustomClaimsBlocking(uid, doc.getUserType(), req.modulos());
                    firebaseAuth.revokeRefreshTokens(uid);
                    log.info("[Gestion] Permisos actualizados y tokens revocados para uid={} por admin={}", uid, adminUid);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    /**
     * Genera link de reset vía Firebase Admin y lo envía por email SMTP.
     * Sirve tanto para usuarios internos como para vendedores/tiendas.
     */
    public Mono<Void> enviarResetPassword(String uid) {
        return userRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado: " + uid)))
                .flatMap(doc -> Mono.fromCallable(() -> {
                    String email  = doc.getEmail();
                    String nombre = doc.getFirstName() != null ? doc.getFirstName() : "Usuario";
                    String link   = firebaseAuth.generatePasswordResetLink(email);
                    enviarEmailReset(email, nombre, link);
                    log.info("[Gestion] Email de reset enviado a uid={}, email={}", uid, email);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    private void enviarEmailReset(String toEmail, String nombre, String resetLink) {
        try {
            var message = mailSender.createMimeMessage();
            var helper  = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom, "Motoya Digital");
            helper.setTo(toEmail);
            helper.setSubject("Restablecer contraseña — Motoya");
            helper.setText("""
                    Hola %s,

                    Recibimos una solicitud para restablecer tu contraseña en la plataforma Motoya.

                    Haz clic en el siguiente enlace para crear una nueva contraseña:

                    %s

                    Este enlace expira en 1 hora. Si no solicitaste este cambio, ignora este correo.

                    — Equipo Motoya
                    """.formatted(nombre, resetLink), false);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[Gestion] Error enviando email de reset a {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("No se pudo enviar el email de reset", e);
        }
    }

    /**
     * Admin setea contraseña directamente (sin que el usuario la conozca previamente).
     */
    public Mono<Void> setPassword(String uid, SetPasswordRequest req) {
        return Mono.fromCallable(() -> {
            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(uid)
                    .setPassword(req.password());
            firebaseAuth.updateUser(updateRequest);
            log.info("[Gestion] Contraseña actualizada directamente para uid={}", uid);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TIENDAS
    // ══════════════════════════════════════════════════════════════════════════

    public Mono<TiendaResponse> crearTienda(CrearTiendaRequest req, String adminUid) {
        return crearFirebaseUser(req.email(), req.password(), req.firstName(), req.lastName())
                .flatMap(uid -> guardarTienda(uid, req, adminUid));
    }

    public Flux<TiendaResponse> listarTiendas() {
        return tiendaRepository.findAll()
                .map(this::toTiendaResponse);
    }

    public Mono<TiendaResponse> obtenerTienda(String uid) {
        return tiendaRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Tienda no encontrada: " + uid)))
                .map(this::toTiendaResponse);
    }

    public Mono<TiendaResponse> cambiarEstadoTienda(String uid, CambiarEstadoRequest req) {
        return tiendaRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Tienda no encontrada: " + uid)))
                .flatMap(doc -> Mono.fromCallable(() -> {
                    // Una tienda suspendida sigue pudiendo iniciar sesión (isActive=true)
                    // pero su portal muestra estado restringido (tiendaStatus='suspendida').
                    // Solo pendiente_aprobacion u otros estados no activos bloquean el acceso.
                    boolean canLogin = "activa".equals(req.estado()) || "suspendida".equals(req.estado());
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("tiendaStatus", req.estado());
                    updates.put("isActive", canLogin);
                    updates.put("updatedAt", Timestamp.now());
                    firestore.collection("tienda_profiles").document(uid).update(updates).get();
                    doc.setTiendaStatus(req.estado());
                    doc.setIsActive(canLogin);
                    return doc;
                }).subscribeOn(Schedulers.boundedElastic()))
                .map(this::toTiendaResponse);
    }

    public Mono<TiendaResponse> actualizarTienda(String uid, ActualizarTiendaRequest req) {
        return tiendaRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Tienda no encontrada: " + uid)))
                .flatMap(doc -> Mono.fromCallable(() -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("firstName",          req.firstName());
                    updates.put("lastName",           req.lastName());
                    updates.put("phone",              req.phone());
                    updates.put("documentType",       req.documentType());
                    updates.put("documentNumber",     req.documentNumber());
                    updates.put("businessName",       req.businessName());
                    updates.put("city",               req.city());
                    updates.put("contactPersonName",  req.contactPersonName());
                    updates.put("contactPersonPhone", req.contactPersonPhone());
                    updates.put("taxId",              req.taxId());
                    updates.put("address",            req.address());
                    updates.put("district",           req.district());
                    updates.put("postalCode",         req.postalCode());
                    updates.put("bankAccount",        req.bankAccount());
                    updates.put("legalRepresentative",req.legalRepresentative());
                    updates.put("website",            req.website());
                    updates.put("facebook",           req.facebook());
                    updates.put("instagram",          req.instagram());
                    updates.put("whatsapp",           req.whatsapp());
                    updates.put("notes",              req.notes());
                    updates.put("updatedAt",          Timestamp.now());
                    firestore.collection("tienda_profiles").document(uid).update(updates).get();

                    Map<String, Object> userUpdates = new HashMap<>();
                    userUpdates.put("firstName",      req.firstName());
                    userUpdates.put("lastName",       req.lastName());
                    userUpdates.put("phone",          req.phone());
                    userUpdates.put("documentType",   req.documentType());
                    userUpdates.put("documentNumber", req.documentNumber());
                    userUpdates.put("updatedAt",      Timestamp.now());
                    firestore.collection("users").document(uid).update(userUpdates).get();

                    doc.setFirstName(req.firstName());
                    doc.setLastName(req.lastName());
                    doc.setPhone(req.phone());
                    doc.setDocumentType(req.documentType());
                    doc.setDocumentNumber(req.documentNumber());
                    doc.setBusinessName(req.businessName());
                    doc.setCity(req.city());
                    doc.setContactPersonName(req.contactPersonName());
                    doc.setContactPersonPhone(req.contactPersonPhone());
                    doc.setTaxId(req.taxId());
                    doc.setAddress(req.address());
                    doc.setDistrict(req.district());
                    doc.setPostalCode(req.postalCode());
                    doc.setBankAccount(req.bankAccount());
                    doc.setLegalRepresentative(req.legalRepresentative());
                    doc.setWebsite(req.website());
                    doc.setFacebook(req.facebook());
                    doc.setInstagram(req.instagram());
                    doc.setWhatsapp(req.whatsapp());
                    doc.setNotes(req.notes());
                    return doc;
                }).subscribeOn(Schedulers.boundedElastic()))
                .map(this::toTiendaResponse);
    }

    public Flux<VendedorResponse> listarVendedoresDeTienda(String tiendaId) {
        return vendedorRepository.findByTiendaId(tiendaId)
                .map(v -> toVendedorResponse(v, null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VENDEDORES
    // ══════════════════════════════════════════════════════════════════════════

    public Mono<VendedorResponse> crearVendedor(CrearVendedorRequest req, String adminUid) {
        // Validar que la tienda existe
        return tiendaRepository.findById(req.tiendaId())
                .switchIfEmpty(Mono.error(new BadRequestException("Tienda no encontrada: " + req.tiendaId())))
                .flatMap(tienda -> crearFirebaseUser(req.email(), req.password(), req.firstName(), req.lastName())
                        .flatMap(uid -> guardarVendedor(uid, req, tienda.getBusinessName(), adminUid)));
    }

    public Flux<VendedorResponse> listarVendedores(String tiendaId) {
        Flux<VendedorProfileDocument> flux = (tiendaId != null && !tiendaId.isBlank())
                ? vendedorRepository.findByTiendaId(tiendaId)
                : vendedorRepository.findAll();

        return flux.map(v -> toVendedorResponse(v, null));
    }

    public Mono<VendedorResponse> obtenerVendedor(String uid) {
        return vendedorRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Vendedor no encontrado: " + uid)))
                .map(v -> toVendedorResponse(v, null));
    }

    public Mono<VendedorResponse> actualizarVendedor(String uid, ActualizarVendedorRequest req) {
        return tiendaRepository.findById(req.tiendaId())
                .switchIfEmpty(Mono.error(new BadRequestException("Tienda no encontrada: " + req.tiendaId())))
                .flatMap(tienda -> vendedorRepository.findById(uid)
                        .switchIfEmpty(Mono.error(new NotFoundException("Vendedor no encontrado: " + uid)))
                        .flatMap(doc -> Mono.fromCallable(() -> {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("firstName",                    req.firstName());
                            updates.put("lastName",                     req.lastName());
                            updates.put("phone",                        req.phone());
                            updates.put("documentType",                 req.documentType());
                            updates.put("documentNumber",               req.documentNumber());
                            updates.put("tiendaId",                     req.tiendaId());
                            updates.put("position",                     req.position());
                            updates.put("commissionRate",               req.commissionRate());
                            updates.put("salesGoal",                    req.salesGoal());
                            updates.put("employeeId",                   req.employeeId());
                            updates.put("supervisorId",                 req.supervisorId());
                            updates.put("experience",                   req.experience());
                            updates.put("education",                    req.education());
                            updates.put("emergencyContactName",         req.emergencyContactName());
                            updates.put("emergencyContactPhone",        req.emergencyContactPhone());
                            updates.put("emergencyContactRelationship", req.emergencyContactRelationship());
                            updates.put("address",                      req.address());
                            updates.put("city",                         req.city());
                            updates.put("district",                     req.district());
                            updates.put("gender",                       req.gender());
                            updates.put("notes",                        req.notes());
                            updates.put("updatedAt",                    Timestamp.now());
                            firestore.collection("vendedor_profiles").document(uid).update(updates).get();

                            Map<String, Object> userUpdates = new HashMap<>();
                            userUpdates.put("firstName",    req.firstName());
                            userUpdates.put("lastName",     req.lastName());
                            userUpdates.put("phone",        req.phone());
                            userUpdates.put("documentType", req.documentType());
                            userUpdates.put("documentNumber", req.documentNumber());
                            userUpdates.put("storeIds",     List.of(req.tiendaId()));
                            userUpdates.put("updatedAt",    Timestamp.now());
                            firestore.collection("users").document(uid).update(userUpdates).get();

                            doc.setFirstName(req.firstName());
                            doc.setLastName(req.lastName());
                            doc.setPhone(req.phone());
                            doc.setDocumentType(req.documentType());
                            doc.setDocumentNumber(req.documentNumber());
                            doc.setTiendaId(req.tiendaId());
                            doc.setPosition(req.position());
                            doc.setCommissionRate(req.commissionRate());
                            doc.setSalesGoal(req.salesGoal());
                            doc.setEmployeeId(req.employeeId());
                            doc.setSupervisorId(req.supervisorId());
                            doc.setExperience(req.experience());
                            doc.setEducation(req.education());
                            doc.setEmergencyContactName(req.emergencyContactName());
                            doc.setEmergencyContactPhone(req.emergencyContactPhone());
                            doc.setEmergencyContactRelationship(req.emergencyContactRelationship());
                            doc.setAddress(req.address());
                            doc.setCity(req.city());
                            doc.setDistrict(req.district());
                            doc.setGender(req.gender());
                            doc.setNotes(req.notes());
                            return toVendedorResponse(doc, tienda.getBusinessName());
                        }).subscribeOn(Schedulers.boundedElastic())));
    }

    public Mono<VendedorResponse> cambiarEstadoVendedor(String uid, CambiarEstadoRequest req) {
        return vendedorRepository.findById(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("Vendedor no encontrado: " + uid)))
                .flatMap(doc -> Mono.fromCallable(() -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("vendedorStatus", req.estado());
                    updates.put("isActive", "activo".equals(req.estado()));
                    updates.put("updatedAt", Timestamp.now());
                    firestore.collection("vendedor_profiles").document(uid).update(updates).get();
                    doc.setVendedorStatus(req.estado());
                    doc.setIsActive("activo".equals(req.estado()));
                    return doc;
                }).subscribeOn(Schedulers.boundedElastic()))
                .map(v -> toVendedorResponse(v, null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS PRIVADOS
    // ══════════════════════════════════════════════════════════════════════════

    private Mono<String> crearFirebaseUser(String email, String password, String firstName, String lastName) {
        return Mono.fromCallable(() -> {
            try {
                UserRecord.CreateRequest createReq = new UserRecord.CreateRequest()
                        .setEmail(email)
                        .setPassword(password)
                        .setDisplayName(firstName + " " + lastName)
                        .setEmailVerified(false);
                UserRecord record = firebaseAuth.createUser(createReq);
                log.info("[Gestion] Firebase Auth user creado: uid={}, email={}", record.getUid(), email);
                return record.getUid();
            } catch (FirebaseAuthException e) {
                String authCode = e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "";
                if ("EMAIL_ALREADY_EXISTS".equals(authCode) ||
                    "EMAIL_EXISTS".equals(authCode) ||
                    (e.getMessage() != null && e.getMessage().contains("EMAIL_EXISTS"))) {
                    throw new ConflictException("El email ya está registrado: " + email);
                }
                throw new RuntimeException("Error creando usuario en Firebase: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> setCustomClaims(String uid, String userType, List<String> modulos) {
        return Mono.fromRunnable(() -> setCustomClaimsBlocking(uid, userType, modulos))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void setCustomClaimsBlocking(String uid, String userType, List<String> modulos) {
        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userType", userType);
            claims.put("modulos", modulos);
            claims.put("roles", List.of(userType.toUpperCase()));
            firebaseAuth.setCustomUserClaims(uid, claims);
        } catch (FirebaseAuthException e) {
            log.warn("[Gestion] No se pudieron setear custom claims para uid={}: {}", uid, e.getMessage());
        }
    }

    private Mono<UsuarioInternoResponse> guardarUsuarioInterno(
            String uid, CrearUsuarioInternoRequest req, List<String> modulos, String adminUid) {

        UserDocument doc = new UserDocument();
        doc.setUid(uid);
        doc.setAuthUID(uid);
        doc.setFirstName(req.firstName());
        doc.setLastName(req.lastName());
        doc.setEmail(req.email());
        doc.setPhone(req.phone());
        doc.setDocumentType(req.documentType());
        doc.setDocumentNumber(req.documentNumber());
        doc.setUserType(req.userType());
        doc.setUserCategory("interno");
        doc.setIsActive(true);
        doc.setIsFirstLogin(true);
        doc.setModulos(modulos);
        doc.setCreatedBy(adminUid);
        doc.setCreatedAt(Timestamp.now());
        doc.setUpdatedAt(Timestamp.now());

        return Mono.fromCallable(() -> {
            firestore.collection("users").document(uid).set(doc).get();
            return toUsuarioInternoResponse(doc);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<TiendaResponse> guardarTienda(String uid, CrearTiendaRequest req, String adminUid) {
        // Documento base en users/
        UserDocument userDoc = new UserDocument();
        userDoc.setUid(uid);
        userDoc.setAuthUID(uid);
        userDoc.setFirstName(req.firstName());
        userDoc.setLastName(req.lastName());
        userDoc.setEmail(req.email());
        userDoc.setPhone(req.phone());
        userDoc.setDocumentType(req.documentType());
        userDoc.setDocumentNumber(req.documentNumber());
        userDoc.setUserType("tienda");
        userDoc.setUserCategory("externo");
        userDoc.setIsActive(true);   // Misma lógica que Electron: activo desde creación; tiendaStatus controla aprobación
        userDoc.setIsFirstLogin(true);
        userDoc.setStoreIds(List.of());  // Tiendas no usan storeIds (el uid = su tienda_profiles doc id)
        userDoc.setModulos(List.of());  // Sin acceso al admin
        userDoc.setCreatedBy(adminUid);
        userDoc.setCreatedAt(Timestamp.now());
        userDoc.setUpdatedAt(Timestamp.now());

        // Documento extendido en tienda_profiles/
        TiendaProfileDocument profileDoc = new TiendaProfileDocument();
        profileDoc.setUid(uid);
        profileDoc.setFirstName(req.firstName());
        profileDoc.setLastName(req.lastName());
        profileDoc.setEmail(req.email());
        profileDoc.setPhone(req.phone());
        profileDoc.setDocumentType(req.documentType());
        profileDoc.setDocumentNumber(req.documentNumber());
        profileDoc.setUserType("tienda");
        profileDoc.setUserCategory("externo");
        profileDoc.setBusinessName(req.businessName());
        profileDoc.setTaxId(req.taxId());
        profileDoc.setAddress(req.address());
        profileDoc.setCity(req.city());
        profileDoc.setDistrict(req.district());
        profileDoc.setPostalCode(req.postalCode());
        profileDoc.setLatitude(req.latitude());
        profileDoc.setLongitude(req.longitude());
        profileDoc.setBankAccount(req.bankAccount());
        profileDoc.setContactPersonName(req.contactPersonName());
        profileDoc.setContactPersonPhone(req.contactPersonPhone());
        profileDoc.setLegalRepresentative(req.legalRepresentative());
        profileDoc.setWebsite(req.website());
        profileDoc.setFacebook(req.facebook());
        profileDoc.setInstagram(req.instagram());
        profileDoc.setWhatsapp(req.whatsapp());
        profileDoc.setTiendaStatus("pendiente_aprobacion");
        profileDoc.setIsActive(false);
        profileDoc.setCreatedBy(adminUid);
        profileDoc.setNotes(req.notes());
        profileDoc.setCreatedAt(Timestamp.now());
        profileDoc.setUpdatedAt(Timestamp.now());

        return Mono.fromCallable(() -> {
            firestore.collection("users").document(uid).set(userDoc).get();
            firestore.collection("tienda_profiles").document(uid).set(profileDoc).get();
            return toTiendaResponse(profileDoc);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<VendedorResponse> guardarVendedor(
            String uid, CrearVendedorRequest req, String tiendaBusinessName, String adminUid) {

        UserDocument userDoc = new UserDocument();
        userDoc.setUid(uid);
        userDoc.setAuthUID(uid);
        userDoc.setFirstName(req.firstName());
        userDoc.setLastName(req.lastName());
        userDoc.setEmail(req.email());
        userDoc.setPhone(req.phone());
        userDoc.setDocumentType(req.documentType());
        userDoc.setDocumentNumber(req.documentNumber());
        userDoc.setUserType("vendedor");
        userDoc.setUserCategory("externo");
        userDoc.setIsActive(true);
        userDoc.setIsFirstLogin(true);
        userDoc.setStoreIds(List.of(req.tiendaId()));  // Referencia a la tienda del vendedor
        userDoc.setModulos(List.of());  // Sin acceso al admin
        userDoc.setCreatedBy(adminUid);
        userDoc.setCreatedAt(Timestamp.now());
        userDoc.setUpdatedAt(Timestamp.now());

        VendedorProfileDocument profileDoc = new VendedorProfileDocument();
        profileDoc.setUid(uid);
        profileDoc.setFirstName(req.firstName());
        profileDoc.setLastName(req.lastName());
        profileDoc.setEmail(req.email());
        profileDoc.setPhone(req.phone());
        profileDoc.setDocumentType(req.documentType());
        profileDoc.setDocumentNumber(req.documentNumber());
        profileDoc.setUserType("vendedor");
        profileDoc.setUserCategory("externo");
        profileDoc.setTiendaId(req.tiendaId());
        profileDoc.setPosition(req.position());
        profileDoc.setEmployeeId(req.employeeId());
        profileDoc.setSupervisorId(req.supervisorId());
        profileDoc.setCommissionRate(req.commissionRate());
        profileDoc.setSalesGoal(req.salesGoal());
        profileDoc.setExperience(req.experience());
        profileDoc.setEducation(req.education());
        if (req.emergencyContactName() != null || req.emergencyContactPhone() != null) {
            Map<String, Object> ec = new HashMap<>();
            if (req.emergencyContactName() != null)         ec.put("name", req.emergencyContactName());
            if (req.emergencyContactPhone() != null)        ec.put("phone", req.emergencyContactPhone());
            if (req.emergencyContactRelationship() != null) ec.put("relationship", req.emergencyContactRelationship());
            profileDoc.setEmergencyContact(ec);
        }
        profileDoc.setAddress(req.address());
        profileDoc.setCity(req.city());
        profileDoc.setDistrict(req.district());
        profileDoc.setGender(req.gender());
        profileDoc.setNotes(req.notes());
        profileDoc.setVendedorStatus("activo");
        profileDoc.setIsActive(true);
        profileDoc.setCreatedBy(adminUid);
        profileDoc.setHireDate(Timestamp.now());
        profileDoc.setCreatedAt(Timestamp.now());
        profileDoc.setUpdatedAt(Timestamp.now());

        return Mono.fromCallable(() -> {
            firestore.collection("users").document(uid).set(userDoc).get();
            firestore.collection("vendedor_profiles").document(uid).set(profileDoc).get();
            return toVendedorResponse(profileDoc, tiendaBusinessName);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAPPERS
    // ══════════════════════════════════════════════════════════════════════════

    private UsuarioInternoResponse toUsuarioInternoResponse(UserDocument doc) {
        return new UsuarioInternoResponse(
                doc.getUid(),
                doc.getFirstName(),
                doc.getLastName(),
                doc.getEmail(),
                doc.getPhone(),
                doc.getDocumentType(),
                doc.getDocumentNumber(),
                doc.getUserType(),
                doc.getUserCategory(),
                doc.getIsActive(),
                doc.getModulos() != null ? doc.getModulos() : List.of(),
                doc.getCreatedBy()
        );
    }

    private TiendaResponse toTiendaResponse(TiendaProfileDocument doc) {
        return new TiendaResponse(
                doc.getUid(),
                doc.getFirstName(),
                doc.getLastName(),
                doc.getEmail(),
                doc.getPhone(),
                doc.getDocumentType(),
                doc.getDocumentNumber(),
                doc.getBusinessName(),
                doc.getTaxId(),
                doc.getLegalRepresentative(),
                doc.getCity(),
                doc.getAddress(),
                doc.getDistrict(),
                doc.getPostalCode(),
                doc.getTiendaStatus(),
                doc.getIsActive(),
                doc.getContactPersonName(),
                doc.getContactPersonPhone(),
                doc.getBankAccount(),
                doc.getWebsite(),
                doc.getFacebook(),
                doc.getInstagram(),
                doc.getWhatsapp(),
                doc.getNotes(),
                doc.getCreatedBy()
        );
    }

    private VendedorResponse toVendedorResponse(VendedorProfileDocument doc, String tiendaBusinessName) {
        return new VendedorResponse(
                doc.getUid(),
                doc.getFirstName(),
                doc.getLastName(),
                doc.getEmail(),
                doc.getPhone(),
                doc.getDocumentType(),
                doc.getDocumentNumber(),
                doc.getTiendaId(),
                tiendaBusinessName,
                doc.getPosition(),
                doc.getVendedorStatus(),
                doc.getIsActive(),
                doc.getCommissionRate(),
                doc.getSalesGoal(),
                doc.getEmployeeId(),
                doc.getSupervisorId(),
                doc.getExperience(),
                doc.getEducation(),
                doc.getEmergencyContactName(),
                doc.getEmergencyContactPhone(),
                doc.getEmergencyContactRelationship(),
                doc.getAddress(),
                doc.getCity(),
                doc.getDistrict(),
                doc.getGender(),
                doc.getNotes(),
                doc.getCreatedBy()
        );
    }
}
