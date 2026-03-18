package com.motoyav2.evaluacion.domain.exception;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;

public class TransicionInvalidaException extends DomainException {
    public TransicionInvalidaException(EstadoSolicitud desde, EstadoSolicitud hacia) {
        super("Transición inválida: " + desde.getFirestoreValue() + " → " + hacia.getFirestoreValue());
    }
}
