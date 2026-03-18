package com.motoyav2.evaluacion.domain.model;

/**
 * Snapshot ligero de los datos relevantes de la verificación de identidad,
 * embebido en el dominio Cliente para facilitar la resolución de nombres verificados.
 * <p>
 * Solo contiene los campos de nombres/apellidos devueltos por la API (RENIEC/Factiliza).
 * El resto de campos de la verificación completa vive en {@code verificacionIdentidad}
 * como mapa anidado en Firestore y se expone a través de {@code VerificacionIdentidadResult}.
 */
public record VerificacionIdentidadSnapshot(
        boolean exitoso,
        String apiNombres,
        String apiApellidoPaterno,
        String apiApellidoMaterno
) {}
