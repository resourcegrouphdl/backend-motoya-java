package com.motoyav2.cobranza.domain.exception;

import com.motoyav2.shared.exception.ConflictException;
import lombok.Getter;

@Getter
public class OperacionDuplicadaException extends ConflictException {

    private final String numeroOperacion;
    private final String banco;
    private final String voucherIdExistente;

    public OperacionDuplicadaException(String numeroOperacion, String banco, String voucherIdExistente) {
        super("La operación " + numeroOperacion + " del banco " + banco
                + " ya fue registrada en el sistema (voucherId existente: " + voucherIdExistente + ")");
        this.numeroOperacion    = numeroOperacion;
        this.banco              = banco;
        this.voucherIdExistente = voucherIdExistente;
    }
}