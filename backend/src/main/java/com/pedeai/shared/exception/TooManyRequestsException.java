package com.pedeai.shared.exception;

/** Tentativas demais em pouco tempo (senha, código de pareamento): espere e tente de novo (429). */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
