package com.pedeai.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Revise os campos informados.", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Não foi possível ler os dados enviados.", request, Map.of());
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            HandlerMethodValidationException.class
    })
    ResponseEntity<ApiError> handleBadParameter(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro inválido na requisição.", request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso não encontrado.", request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "Este registro foi alterado por outra pessoa. Recarregue e tente de novo.", request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Violação de integridade em {}: {}", request.getRequestURI(), exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Os dados enviados conflitam com um registro existente.", request, Map.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Faça login para continuar.", request, Map.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiError> handleForbiddenOperation(ForbiddenOperationException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Você não tem permissão para esta ação.", request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método não suportado neste endereço.", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado. Tente novamente em instantes.", request, Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           Map<String, String> fields) {
        ApiError body = new ApiError(Instant.now(clock), status.value(), message, request.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
