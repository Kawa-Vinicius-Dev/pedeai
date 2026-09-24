package com.pedeai.shared.exception;

/** Duplicidade ou conflito com o estado atual do registro (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
