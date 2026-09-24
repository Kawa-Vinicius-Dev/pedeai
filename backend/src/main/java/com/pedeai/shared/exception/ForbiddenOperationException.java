package com.pedeai.shared.exception;

/** Operação que ninguém pode fazer no momento, independentemente do papel (403). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
