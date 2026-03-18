package com.motoyav2.evaluacion.shared.exception;

import com.motoyav2.evaluacion.domain.exception.DomainException;

public class RecursoNoEncontradoException extends DomainException {
    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
