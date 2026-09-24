package com.pedeai.shared.exception;

/** Login com e-mail ou senha errados, ou sessão (refresh token) inválida (401). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
