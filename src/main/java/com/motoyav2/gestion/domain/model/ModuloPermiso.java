package com.motoyav2.gestion.domain.model;

import java.util.List;

/**
 * Constantes de los módulos del sistema admin.
 * Cada ID corresponde a una sección del menú del frontend.
 */
public final class ModuloPermiso {

    private ModuloPermiso() {}

    public static final String EVALUACIONES       = "evaluaciones";
    public static final String CONTRATOS          = "contratos";
    public static final String COBRANZAS          = "cobranzas";
    public static final String VALIDACION         = "validacion";
    public static final String FINANZAS           = "finanzas";
    public static final String CALCULADORA        = "calculadora";
    public static final String CALCULADORA_CONFIG = "calculadora.config";
    public static final String GESTION_USUARIOS   = "gestion-usuarios";
    public static final String AUDIT_LOG          = "audit-log";

    /** Lista completa de todos los módulos disponibles */
    public static final List<String> ALL = List.of(
            EVALUACIONES, CONTRATOS, COBRANZAS, VALIDACION, FINANZAS,
            CALCULADORA, CALCULADORA_CONFIG, GESTION_USUARIOS, AUDIT_LOG
    );

    /**
     * Módulos por defecto según el tipo de usuario.
     * Solo se usan cuando el usuario no tiene módulos configurados explícitamente.
     */
    public static List<String> defaultForRole(String userType) {
        if (userType == null) return List.of();
        return switch (userType.toLowerCase()) {
            case "admin"      -> ALL;
            case "supervisor" -> List.of(EVALUACIONES, CONTRATOS, COBRANZAS, FINANZAS, CALCULADORA);
            case "evaluador"  -> List.of(EVALUACIONES, CONTRATOS);
            case "asesor"     -> List.of(CONTRATOS, CALCULADORA);
            default           -> List.of();
        };
    }
}
