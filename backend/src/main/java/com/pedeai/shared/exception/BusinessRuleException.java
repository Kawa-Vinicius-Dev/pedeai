package com.pedeai.shared.exception;

/** A requisição é válida no formato, mas uma regra de negócio a recusa (422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
