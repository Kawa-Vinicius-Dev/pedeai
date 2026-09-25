package com.pedeai.shared.exception;

/** Recurso inexistente ou de outra loja. As duas situações respondem 404, para não revelar o que existe. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
