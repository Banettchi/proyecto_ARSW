package com.shark.auth.exception;

import com.shark.auth.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({UsernameAlreadyExistsException.class, EmailAlreadyExistsException.class})
    public ResponseEntity<ErrorResponse> handleConflictExceptions(RuntimeException ex) {
        return buildErrorResponse(ex.getMessage(), null, HttpStatus.CONFLICT);
    }

    @ExceptionHandler({InvalidUsernameFormatException.class, WeakPasswordException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(RuntimeException ex) {
        return buildErrorResponse(ex.getMessage(), null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedExceptions(RuntimeException ex) {
        return buildErrorResponse(ex.getMessage(), null, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String message = "Error de validación";
        String field = null;
        if (ex.getBindingResult().hasErrors()) {
            FieldError fieldError = ex.getBindingResult().getFieldErrors().get(0);
            message = fieldError.getDefaultMessage();
            field = fieldError.getField();
        }
        return buildErrorResponse(message, field, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Error interno no controlado: ", ex);
        return buildErrorResponse("Error interno del servidor", null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(String message, String field, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(message, field, Instant.now());
        return ResponseEntity.status(status).body(error);
    }
}
