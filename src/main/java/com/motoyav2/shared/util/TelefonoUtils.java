package com.motoyav2.shared.util;

/**
 * Utilidades de normalización de teléfonos peruanos.
 * Formato interno Motoya: 9 dígitos sin prefijo (como se almacena en clienteTelefono).
 */
public final class TelefonoUtils {

    private TelefonoUtils() {}

    /**
     * Convierte cualquier formato peruano → 9 dígitos.
     * "+51987654321", "51987654321", "987654321" → "987654321"
     * Retorna "" si el input es nulo o no es un teléfono peruano reconocible.
     */
    public static String aNueveDig(String telefono) {
        if (telefono == null) return "";
        String digits = telefono.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        if (digits.length() == 9) return digits;
        return "";
    }

    /**
     * Convierte cualquier formato peruano → "+51XXXXXXXXX".
     * "987654321", "51987654321", "+51987654321" → "+51987654321"
     * Retorna "" si no es un teléfono reconocible.
     */
    public static String aNormalizado(String telefono) {
        String nueve = aNueveDig(telefono);
        return nueve.isBlank() ? "" : "+51" + nueve;
    }
}
